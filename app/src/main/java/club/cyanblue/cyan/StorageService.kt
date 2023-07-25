package club.cyanblue.cyan

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

enum class App(val url: String,
               val icon: Int) {
    Twitter("https://twitter.com/", R.drawable.twitter),
    GitHub("https://github.com/", R.drawable.github),
    GitLab("https://gitlab.com/", R.drawable.gitlab),
    Instagram("https://instagram.com/", R.drawable.instagram),
    SnapChat("https://www.snapchat.com/add/", R.drawable.snapchat),
    Email("mailto:", R.drawable.email),
    Phone("tel:", R.drawable.phone),
//    Text("sms:", R.drawable.phone)
}
val apps = App.values()

data class Account(
    var id: String,
    var app: App,
    var username: String,
    var personal: Boolean
)

data class User(
    val uid: String,
    var username: String,
    var fullname: String,
    val accounts: MutableList<Account>
)

interface StorageService {
    fun getUser(uid: String, personal: Boolean, onError: (Throwable) -> Unit, onSuccess: (User) -> Unit)

    fun addAccount(user: User, account: Account, onSuccess: () -> Unit, onError: (Throwable) -> Unit)
    fun removeAccount(user: User, account: Account, onSuccess: () -> Unit, onError: (Throwable) -> Unit)
    fun updateAccount(user: User, account: Account, onSuccess: () -> Unit, onError: (Throwable) -> Unit)
}

class FireStoreService(private val db: FirebaseFirestore) : StorageService {
    override fun getUser(uid: String, personal: Boolean, onError: (Throwable) -> Unit, onSuccess: (User) -> Unit) {
        val u = db.collection("users").document(uid)
        val a = if (personal) u.collection("accounts")
        else u.collection("accounts").whereEqualTo("personal", false)


        Tasks.whenAllSuccess<Any>(u.get(), a.get()).addOnSuccessListener {
            it.let { (user, accounts) -> Pair(user as DocumentSnapshot, accounts as QuerySnapshot) }
                .also { (user, accounts) ->
                    onSuccess(
                        User(
                            uid,
                            user["username"] as String,
                            user["fullname"] as String,
                            accounts.map { account ->
                                Account(
                                    account.id,
                                    apps[(account.data["app"] as? Number ?: 0).toInt()],
                                    account.data["username"] as String,
                                    account.data["personal"] as? Boolean ?: true
                                )
                            } as MutableList<Account>
                        )
                    )
                }
        }.addOnFailureListener { throw it }
    }

    override fun addAccount(user: User, account: Account, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        user.accounts.add(account)
        db
            .collection("users")
            .document(user.uid)
            .collection("accounts")
            .add(mapOf(
                "app" to account.app.ordinal,
                "username" to account.username,
                "personal" to account.personal
            ))
            .addOnSuccessListener {
                account.id = it.id
                user.accounts.sortBy { a -> a.id }
                onSuccess()
            }.addOnFailureListener (onError)
    }

    override fun removeAccount(
        user: User,
        account: Account,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        user.accounts.remove(account)
        db
            .collection("users")
            .document(user.uid)
            .collection("accounts")
            .document(account.id)
            .delete()
            .addOnSuccessListener {
                onSuccess()
            }.addOnFailureListener (onError)
    }

    override fun updateAccount(
        user: User,
        account: Account,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        db
            .collection("users")
            .document(user.uid)
            .collection("accounts")
            .document(account.id)
            .set(mapOf(
                "app" to account.app.ordinal,
                "username" to account.username,
                "personal" to account.personal
            ))
            .addOnSuccessListener {
                onSuccess()
            }.addOnFailureListener (onError)
    }
}