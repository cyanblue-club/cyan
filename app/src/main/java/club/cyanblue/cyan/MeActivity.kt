package club.cyanblue.cyan

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import club.cyanblue.cyan.ui.theme.CyanTheme
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MeTileService: TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.label = "Me"
        qsTile.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.qs_icon)
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivityAndCollapse(intent)
    }
}

class MeActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
        auth = Firebase.auth
        val currentUser = auth.currentUser
        val db = Firebase.firestore
        val userService = FireStoreService(db)

        setContent {
            var user: User? by remember { mutableStateOf(null) }

            intent.data?.lastPathSegment.let { if (it.isNullOrBlank()) currentUser?.uid else it }?.let { uid ->
                userService.getUser(uid, false, onError = {
                    Log.e("Log", it.toString())
                }) {
                    user = it
                }
            }

            CyanTheme {
//                var nfc by remember { mutableStateOf(false) }
                val bottomSheetState = rememberModalBottomSheetState()

                ModalBottomSheet(
                    onDismissRequest = { this.finish() },
                    sheetState = bottomSheetState,
                    windowInsets = BottomSheetDefaults.windowInsets
                ) {
                    Column {
                        if (user == null) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Me(this@MeActivity, user!!)
                        }
                    }
                }
//                if (nfc) {
//                    AlertDialog(
//                        onDismissRequest = {
//                            nfcAdapter.setNdefPushMessage(
//                                null,
//                                this@MeActivity
//                            )
//                            nfc = false
//                        },
//                        title = {
//                            Text(text = "Tap")
//                        },
//                        text = {
//                            Text(text = "Sharing by NFC...")
//                        },
//                        confirmButton = {
//                            TextButton(
//                                onClick = {
//                                    nfcAdapter.setNdefPushMessage(
//                                        null,
//                                        this@MeActivity
//                                    )
//                                    nfc = false
//                                }
//                            ) {
//                                Text("Stop")
//                            }
//                        }
//                    )
//                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Me(context: Activity, user: User, onOpen: (Account) -> Unit = { account ->
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${account.app.url}${account.username}")))
}) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text("@${user.username}", fontSize = 5.em)
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    ) {
        Text(user.fullname, color = Color.Gray)
    }
    var personal by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 20.dp)
    ) {
        if (personal) QrCode("https://www.cyanblue.club/personal/${user.uid}")
        else QrCode("https://www.cyanblue.club/${user.uid}")
    }
    FlowRow(horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)) {
        SuggestionChip(onClick = {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "https://www.cyanblue.club${if (personal) "/personal" else ""}/${user.uid}")
                putExtra(Intent.EXTRA_PACKAGE_NAME, "com.google.android.gps")
                type = "text/plain"
            }

            context.startActivity(sendIntent)
        }, label = { Text("Nearby Share") }, icon = { Icon(Icons.Rounded.Send, "Nearby Share") }, modifier = Modifier.padding(horizontal = 5.dp))
        SuggestionChip(onClick = {
            // Creates a new Intent to insert or edit a contact
            val intentInsertEdit = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                // Sets the MIME type
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra("finishActivityOnSaveCompleted", true)

                // Inserts a name
                putExtra(ContactsContract.Intents.Insert.NAME, user.fullname)
                // Inserts an email address
                putExtra(ContactsContract.Intents.Insert.EMAIL, "me@josephabbey.dev")
                putExtra(
                    ContactsContract.Intents.Insert.EMAIL_TYPE,
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME
                )
                // Inserts a phone number
                putExtra(ContactsContract.Intents.Insert.PHONE, "07740241013")
                putExtra(
                    ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
            }
            // Add code here to insert extended data, if desired

            // Sends the Intent with an request ID
            context.startActivity(intentInsertEdit)
        }, label = { Text("Add to Contacts") }, icon = { Icon(Icons.Rounded.AddCircle, "Contacts") }, modifier = Modifier.padding(horizontal = 5.dp))
        //                            SuggestionChip(onClick = {
        //                                nfcAdapter.setNdefPushMessage(NdefMessage(
        //                                    NdefRecord(
        //                                        TNF_ABSOLUTE_URI,
        //                                        "https://www.cyanblue.club${if (personal) "/personal" else ""}/${user.uid}".toByteArray(Charset.forName("US-ASCII")),
        //                                        null,
        //                                        null
        //                                    )
        //                                ), this@MeActivity)
        //                                nfc = true
        //                            }, label = { Text("Tap") }, icon = { Icon(Icons.Rounded.ExitToApp, "Tap Share") }, modifier = Modifier.padding(horizontal = 5.dp))
        if (user.accounts.indexOfFirst { it.personal } != -1)
            SuggestionChip(onClick = {
                personal = !personal
            }, label = { Text(if (personal) "Hide Personal" else "Show Personal") }, icon = { Icon(if (personal) Icons.Rounded.Close else Icons.Rounded.Face, "Personal") }, modifier = Modifier.padding(horizontal = 5.dp))
    }
    FlowRow(
        maxItemsInEachRow = 4,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)) {
        user.accounts.forEach {
            Open(it.app, enabled = personal || !it.personal) { onOpen(it) }
        }
    }
}

@Composable
fun Open(app: App, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier
        .size(60.dp)
        .padding(horizontal = 5.dp)) {
        Surface(shape = CircleShape, color = if (enabled) Color.White else Color.Gray) {
            Image(painterResource(app.icon), app.name, modifier = Modifier.padding(3.dp), colorFilter = if (enabled) null else ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.33f, 0.33f, 0.33f, 0f, 0f,
                        0.33f, 0.33f, 0.33f, 0f, 0f,
                        0.33f, 0.33f, 0.33f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            ))
        }
    }
}

@Composable
fun QrCode(url: String) {
    val qr = rememberQrBitmapPainter(url, padding = 1.dp);

    Surface(shape = RoundedCornerShape(10.dp)) {
        Image(
            painter = qr,
            contentDescription = url,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(250.dp),
        )
    }
}

@Preview
@Composable
fun QrCodePreview() {
    QrCode("@__JosephAbbey")
}

// https://gist.github.com/dev-niiaddy/8f936062291e3d328c7d10bb644273d0
@Composable
fun rememberQrBitmapPainter(
    content: String,
    size: Dp = 150.dp,
    padding: Dp = 0.dp
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val paddingPx = with(density) { padding.roundToPx() }


    var bitmap by remember(content) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        if (bitmap != null) return@LaunchedEffect

        launch(Dispatchers.IO) {
            val qrCodeWriter = QRCodeWriter()

            val encodeHints = mutableMapOf<EncodeHintType, Any?>()
                .apply {
                    this[EncodeHintType.MARGIN] = paddingPx
                }

            val bitmapMatrix = try {
                qrCodeWriter.encode(
                    content, BarcodeFormat.QR_CODE,
                    sizePx, sizePx, encodeHints
                )
            } catch (ex: WriterException) {
                null
            }

            val matrixWidth = bitmapMatrix?.width ?: sizePx
            val matrixHeight = bitmapMatrix?.height ?: sizePx

            val newBitmap = Bitmap.createBitmap(
                bitmapMatrix?.width ?: sizePx,
                bitmapMatrix?.height ?: sizePx,
                Bitmap.Config.ARGB_8888,
            )

            for (x in 0 until matrixWidth)
                for (y in 0 until matrixHeight)
                    newBitmap.setPixel(x, y, if (bitmapMatrix?.get(x, y) == true) android.graphics.Color.BLACK else android.graphics.Color.WHITE)

            bitmap = newBitmap
        }
    }

    return remember(bitmap) {
        val currentBitmap = bitmap ?: Bitmap.createBitmap(
            sizePx, sizePx,
            Bitmap.Config.ARGB_8888,
        ).apply { eraseColor(android.graphics.Color.WHITE) }

        BitmapPainter(currentBitmap.asImageBitmap())
    }
}
