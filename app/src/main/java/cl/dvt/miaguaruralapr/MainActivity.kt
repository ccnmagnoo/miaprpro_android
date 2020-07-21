package cl.dvt.miaguaruralapr

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import cl.dvt.miaguaruralapr.A01SplashActivity.Companion.currentApr
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_f01_consumo.*
import kotlinx.android.synthetic.main.section_toolbar_main.*
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {
    companion object{
        var QUERY_KEY:String? = "QUERY_WORD"
        var block_key:Boolean = false
        //permisos de uso de cámara
        const val permissionCode = 1000
        var camPermissionBoolean = false
        var requestCameraResult = false

        //permisos del uso del GPS

        //días de operación remanentes
        var remainingDays:Short = 0 /* RES : https://www.mkyong.com/java/java-how-to-add-days-to-current-date/ */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        verifyUserIsLoggedIn() /* verificando usuario si no ir a login */

        configureTabLayout()   /* instando pestañas */

        requestCameraResult = requestCameraPermission() /* permisos de cámara */
        remainingDays = getRemainingDays(currentApr)

        search_searchView_toolbar.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d("Search", "query word : $query")
                val intent = Intent(this@MainActivity, A04SearchActivity::class.java)
                intent.putExtra(QUERY_KEY, query)
                startActivity(intent)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

    }

    //F01. FUNCIÓN verificar Loggin;
    private fun verifyUserIsLoggedIn(){
        val uid = FirebaseAuth.getInstance().uid /**buscar la uid actual*/
        Log.d("CurrentUser", "UID : $uid")
        val ref = FirebaseFirestore.getInstance().collection("userApr").document("$uid")
        if (uid==null){
            val intent = Intent(this, A02LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

    }/**fin verifyUserIsLoggedIn*/

    //F02. FUNCIÓN TabLayout
    private fun configureTabLayout(){
        tabLayout_main.addTab(tabLayout_main.newTab().setText("Consumo").setIcon(R.drawable.ic_ico_clock_24dp))
        tabLayout_main.addTab(tabLayout_main.newTab().setText("Usuarios").setIcon(R.drawable.ic_ico_person_20dp))
        tabLayout_main.addTab(tabLayout_main.newTab().setText("Tramos").setIcon(R.drawable.ic_ico_coin_20dp))
        tabLayout_main.addTab(tabLayout_main.newTab().setText("Producción").setIcon(R.drawable.ic_ico_production_20dp))
        val fragmentAdapter = X01MainTabPagerAdapter(
            supportFragmentManager,
            tabLayout_main.tabCount
        )
        viewPager_main.adapter = fragmentAdapter
        viewPager_main.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout_main))
        tabLayout_main.addOnTabSelectedListener(object :
            TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?){
                viewPager_main.currentItem = tab!!.position
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
            }
        )
    }

    //F03. Acciones en selección de items de ToolBar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val x = item.itemId
        Log.d("Menu", "item menu id selected : $x")
        when (item.itemId){
            /*ir a pantalla de login*/
            R.id.logOut_menuItem_main ->{
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, A02LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK.or(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    //F04 Permisos de cámara
    private fun requestCameraPermission():Boolean{
        /*if system os is Marshmallow or Above, we need to request runtime permission*/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (ActivityCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)== PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED){
                /* permission was not enabled */
                val permission = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                /* show popup to request permission */
                requestPermissions(permission, permissionCode)
            }else{
                //permission already granted
                /*openCamera()*/
                return true}
        }else{
            //system os is < marshmallow
            /*openCamera()*/
            return true}
        return false
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        /* called when user presses ALLOW or DENY from Permission Request Popup */
        when(requestCode){
            permissionCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    //openCamera()
                    camPermissionBoolean = true
                }
                else{
                    Toast.makeText(this, "cámara denegada", Toast.LENGTH_SHORT).show()}
            }
        }
    }




    //Calculate Remaining Days for operation
    private fun getRemainingDays(apr:AprObject?):Short{
        return when (apr?.planId){
            30      ->  {
                30 /* id 30: plan gratuito inicial */
            }
            null    ->  {
                0 /* id null: error no hay plan */
            }
            else    ->  {
                val currentTime     = Calendar.getInstance()
                val dateLastPayment = Calendar.getInstance()
                dateLastPayment.time          = apr.dateLimitBuy /* fecha límite de operación */
                val days = TimeUnit.MILLISECONDS.toDays((dateLastPayment.timeInMillis - currentTime.timeInMillis)).toShort()
                Log.d("Consumption", "Tiempo remanente para carga de consumos $days dias")

                days
            }
        }
    }

}

