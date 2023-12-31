package club.cyanblue.cyan

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

enum class App(
    val url: String,
    val icon: Int,
    val packageId: String? = null
) {
    Twitter("https://twitter.com/", R.drawable.twitter, "com.twitter.android"),
    GitHub("https://github.com/", R.drawable.github, "com.github.android"),
    GitLab("https://gitlab.com/", R.drawable.gitlab,),
    Instagram("https://instagram.com/", R.drawable.instagram, "com.instagram.android"),
    SnapChat("https://www.snapchat.com/add/", R.drawable.snapchat, "com.snapchat.android"),
    Email("mailto:", R.drawable.email, "com.google.android.gm"),
    Phone("tel:", R.drawable.phone, "com.google.android.dialer"),
}

val apps = App.values()

data class Account(
    var id: String,
    var app: App,
    var username: String
)

data class User(
    val uid: String,
    var username: String,
    var fullname: String,
    val accounts: MutableList<Account>,
    var personalAccounts: MutableList<Account>?,
    var secret: String?
)

interface StorageService {
    fun getUser(
        uid: String,
        personal: Boolean,
        onError: (Throwable) -> Unit,
        onSuccess: (User) -> Unit
    )

    fun getPrivateAccounts(
        user: User,
        onError: (Throwable) -> Unit,
        onSuccess: (List<Account>) -> Unit
    )

    fun addAccount(
        user: User,
        account: Account,
        personal: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    )

    fun removeAccount(
        user: User,
        account: Account,
        personal: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    )

    fun updateAccount(
        user: User,
        account: Account,
        personal: Boolean,
        previouselyPersonal: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    )
}

class FireStoreService(private val db: FirebaseFirestore) : StorageService {
    override fun getUser(
        uid: String,
        personal: Boolean,
        onError: (Throwable) -> Unit,
        onSuccess: (User) -> Unit
    ) {
        val u = db.collection("users").document(uid)
        val a = u.collection("accounts")
        val s = if (personal) u.collection("priv").document("ate")
        else null

        val tasks = mutableListOf(u.get(), a.get())
        if (s != null) tasks += s.get()

        Tasks.whenAllSuccess<Any>(tasks).addOnSuccessListener {
            it.let { results ->
                Triple(
                    results[0] as DocumentSnapshot,
                    results[1] as QuerySnapshot,
                    results.getOrNull(2) as DocumentSnapshot?
                )
            }
                .also { (user, accounts, secret) ->
                    onSuccess(
                        User(
                            uid,
                            user["username"] as String,
                            user["fullname"] as String,
                            accounts.map { account ->
                                Account(
                                    account.id,
                                    apps[(account.data["app"] as? Number ?: 0).toInt()],
                                    account.data["username"] as String
                                )
                            } as MutableList<Account>,
                            null,
                            secret?.get("secret") as? String ?: ""
                        )
                    )
                }
        }.addOnFailureListener(onError)
    }

    override fun getPrivateAccounts(
        user: User,
        onError: (Throwable) -> Unit,
        onSuccess: (List<Account>) -> Unit
    ) {
        db.collection("users")
            .document(user.uid)
            .collection("priv")
            .document(user.secret!!)
            .collection("accounts")
            .get()
            .addOnSuccessListener {
                it?.let { accounts ->
                    onSuccess(
                        accounts.map { account ->
                            Account(
                                account.id,
                                apps[(account.data["app"] as? Number ?: 0).toInt()],
                                account.data["username"] as String
                            )
                        } as MutableList<Account>
                    )
                }
            }.addOnFailureListener(onError)
    }

    override fun addAccount(
        user: User,
        account: Account,
        personal: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (personal) {
            user.personalAccounts!!.add(account)
            if (account.id.isEmpty()) {
                db.collection("users")
                    .document(user.uid)
                    .collection("priv")
                    .document(user.secret!!)
                    .collection("accounts")
                    .add(
                        mapOf(
                            "app" to account.app.ordinal,
                            "username" to account.username
                        )
                    )
                    .addOnSuccessListener {
                        account.id = it.id
                        user.accounts.sortBy { a -> a.id }
                        onSuccess()
                    }.addOnFailureListener(onError)
            } else {
                db.collection("users")
                    .document(user.uid)
                    .collection("priv")
                    .document(user.secret!!)
                    .collection("accounts")
                    .document(account.id)
                    .set(
                        mapOf(
                            "app" to account.app.ordinal,
                            "username" to account.username
                        )
                    )
                    .addOnSuccessListener {
                        onSuccess()
                    }.addOnFailureListener(onError)
            }
        } else {
            if (account.id.isEmpty()) {
                user.accounts.add(account)
                db.collection("users")
                    .document(user.uid)
                    .collection("accounts")
                    .add(
                        mapOf(
                            "app" to account.app.ordinal,
                            "username" to account.username
                        )
                    )
                    .addOnSuccessListener {
                        account.id = it.id
                        user.accounts.sortBy { a -> a.id }
                        onSuccess()
                    }.addOnFailureListener(onError)
            } else {
                db.collection("users")
                    .document(user.uid)
                    .collection("accounts")
                    .document(account.id)
                    .set(
                        mapOf(
                            "app" to account.app.ordinal,
                            "username" to account.username
                        )
                    )
                    .addOnSuccessListener {
                        onSuccess()
                    }.addOnFailureListener(onError)
            }
        }

    }

    override fun removeAccount(
        user: User,
        account: Account,
        personal: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (personal) {
            user.personalAccounts!!.remove(account)
            db
                .collection("users")
                .document(user.uid)
                .collection("priv")
                .document(user.secret!!)
                .collection("accounts")
                .document(account.id)
                .delete()
                .addOnSuccessListener {
                    onSuccess()
                }.addOnFailureListener(onError)
        } else {
            user.accounts.remove(account)
            db
                .collection("users")
                .document(user.uid)
                .collection("accounts")
                .document(account.id)
                .delete()
                .addOnSuccessListener {
                    onSuccess()
                }.addOnFailureListener(onError)
        }
    }

    override fun updateAccount(
        user: User,
        account: Account,
        personal: Boolean,
        previouselyPersonal: Boolean,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (previouselyPersonal && personal) {
            db
                .collection("users")
                .document(user.uid)
                .collection("priv")
                .document(user.secret!!)
                .collection("accounts")
                .document(account.id)
                .set(
                    mapOf(
                        "app" to account.app.ordinal,
                        "username" to account.username
                    )
                )
                .addOnSuccessListener {
                    onSuccess()
                }.addOnFailureListener(onError)
        } else if (!(previouselyPersonal || personal)) {
            db
                .collection("users")
                .document(user.uid)
                .collection("accounts")
                .document(account.id)
                .set(
                    mapOf(
                        "app" to account.app.ordinal,
                        "username" to account.username
                    )
                )
                .addOnSuccessListener {
                    onSuccess()
                }.addOnFailureListener(onError)
        } else {
            removeAccount(user, account, previouselyPersonal, {
                addAccount(user, account, personal, onSuccess, onError)
            }, onError)
        }
    }
}
