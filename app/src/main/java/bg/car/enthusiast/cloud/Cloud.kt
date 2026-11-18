package bg.car.enthusiast.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import bg.car.enthusiast.R
import bg.car.enthusiast.core.Data
import bg.car.enthusiast.data.Json
import bg.car.enthusiast.data.Store
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Общ „гараж“ във Firestore: garages/{id} с полета emails и owner,
 * а колите, сроковете и ремонтите са в подколекции cars, items и repairs (по един документ с поле j = JSON).
 * Локалният Store остава кеш, от който четат известията.
 */
object Cloud {
    private const val CARS = "cars"
    private const val ITEMS = "items"
    private const val REPAIRS = "repairs"

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    val email: String? get() = auth.currentUser?.email?.lowercase()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    fun garageId(ctx: Context): String? = if (email == null) null else prefs(ctx).getString("garage", null)
    private fun setGarage(ctx: Context, id: String?) = prefs(ctx).edit().putString("garage", id).apply()

    /** Вход с Google през Credential Manager и Firebase. Трябва Activity за прозореца за избор на акаунт. */
    suspend fun signIn(activity: Context): String {
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Неочакван вид вход")
        }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        return email ?: error("Google не върна имейл")
    }

    fun signOut(ctx: Context) {
        auth.signOut()
        setGarage(ctx, null)
    }

    /**
     * Избира гаража: ако някой е споделил своя с този имейл, влизаме в неговия, иначе в собствения.
     * Локалните данни се качват в избрания гараж, за да не се губи нищо.
     */
    suspend fun connect(ctx: Context): String {
        val me = email ?: error("Не си влязъл")
        val found = db.collection("garages").whereArrayContains("emails", me).get().await().documents
        val best = found.maxWithOrNull(
            compareBy<DocumentSnapshot> { (it.get("emails") as? List<*>)?.size ?: 0 }
                .thenByDescending { it.getLong("created") ?: Long.MAX_VALUE },
        )
        val id = best?.id ?: db.collection("garages").document().also {
            it.set(
                mapOf(
                    "emails" to listOf(me),
                    "owner" to me,
                    "created" to System.currentTimeMillis(),
                ),
            ).await()
        }.id
        val changed = id != prefs(ctx).getString("garage", null)
        setGarage(ctx, id)
        if (changed) push(ctx, Data(), Store(ctx).load())
        return id
    }

    suspend fun members(ctx: Context): List<String> {
        val id = garageId(ctx) ?: return emptyList()
        val doc = db.collection("garages").document(id).get().await()
        return (doc.get("emails") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    }

    suspend fun addMember(ctx: Context, mail: String) {
        val id = garageId(ctx) ?: return
        db.collection("garages").document(id).update("emails", FieldValue.arrayUnion(mail.trim().lowercase())).await()
    }

    suspend fun removeMember(ctx: Context, mail: String) {
        val id = garageId(ctx) ?: return
        db.collection("garages").document(id).update("emails", FieldValue.arrayRemove(mail)).await()
    }

    /** Записва в облака само разликата между старите и новите данни. */
    fun push(ctx: Context, old: Data, new: Data) {
        val id = garageId(ctx) ?: return
        val g = db.collection("garages").document(id)
        val batch = db.batch()
        var n = 0
        fun <T> diff(col: String, a: List<T>, b: List<T>, key: (T) -> Long, json: (T) -> JSONObject) {
            val before = a.associateBy(key)
            val after = b.associateBy(key)
            after.forEach { (k, v) ->
                if (before[k] != v) {
                    batch.set(g.collection(col).document(k.toString()), mapOf("j" to json(v).toString()))
                    n++
                }
            }
            before.keys.filter { it !in after }.forEach {
                batch.delete(g.collection(col).document(it.toString()))
                n++
            }
        }
        diff(CARS, old.cars, new.cars, { it.id }, { Json.car(it) })
        diff(ITEMS, old.items, new.items, { it.id }, { Json.item(it) })
        diff(REPAIRS, old.repairs, new.repairs, { it.id }, { Json.repair(it) })
        if (n > 0) batch.commit()
    }

    private fun toData(local: Data, cars: QuerySnapshot, items: QuerySnapshot, repairs: QuerySnapshot): Data {
        fun objs(q: QuerySnapshot) = q.documents.mapNotNull { d -> d.getString("j")?.let { runCatching { JSONObject(it) }.getOrNull() } }
        return local.copy(
            cars = objs(cars).mapNotNull { runCatching { Json.car(it) }.getOrNull() }.sortedBy { it.id },
            items = objs(items).mapNotNull { runCatching { Json.item(it) }.getOrNull() },
            repairs = objs(repairs).mapNotNull { runCatching { Json.repair(it) }.getOrNull() },
        )
    }

    /** Еднократно изтегляне (за дневната проверка на заден план). */
    suspend fun pull(ctx: Context): Data? {
        val id = garageId(ctx) ?: return null
        val g = db.collection("garages").document(id)
        val store = Store(ctx)
        val d = toData(store.load(), g.collection(CARS).get().await(), g.collection(ITEMS).get().await(), g.collection(REPAIRS).get().await())
        store.save(d)
        return d
    }

    /** Следи промените от другите телефони. Връща функция за спиране. */
    fun listen(ctx: Context, onData: (Data) -> Unit): () -> Unit {
        val id = garageId(ctx) ?: return {}
        val g = db.collection("garages").document(id)
        val snaps = arrayOfNulls<QuerySnapshot>(3)
        val store = Store(ctx)
        fun update() {
            val c = snaps[0] ?: return
            val i = snaps[1] ?: return
            val r = snaps[2] ?: return
            val d = toData(store.load(), c, i, r)
            store.save(d)
            onData(d)
        }
        val regs: List<ListenerRegistration> = listOf(CARS, ITEMS, REPAIRS).mapIndexed { idx, col ->
            g.collection(col).addSnapshotListener { q, _ ->
                if (q != null) {
                    snaps[idx] = q
                    update()
                }
            }
        }
        return { regs.forEach { it.remove() } }
    }
}
