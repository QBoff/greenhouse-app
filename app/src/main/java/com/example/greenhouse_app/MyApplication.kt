package com.example.greenhouse_app

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.room.Room
import com.example.greenhouse_app.dataClasses.AllData
import com.example.greenhouse_app.dataClasses.ListForData
import com.example.greenhouse_app.recyclerView.DataAdapter
import com.example.greenhouse_app.fragments.ApiListener
import com.example.greenhouse_app.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.round
import kotlin.math.roundToInt

fun getCurrentDateTimeISO8601(): String {
    val formatter = DateTimeFormatter.ISO_DATE_TIME
    return Instant.now().toString()
}

class MyApplication : Application() {
    val DEBUGMODE = false

    private lateinit var networkManager: AppNetworkManager
    private lateinit var handler: Handler
    private lateinit var db: AppDatabase
    private lateinit var sensorDao: SensorDao
    private var decimalFormatter = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
    private var apiListener: ApiListener? = null
    val myAdapter by lazy { DataAdapter() }
    var currentUID: String = "DEBUG"
        set(uid) {
            field = uid
            loggedIn = true

            db = AppDatabaseHelper.getDatabase(applicationContext, uid)
            sensorDao = db.SensorDao()

            networkManager = AppNetworkManager(applicationContext)
            handler = Handler(Looper.getMainLooper())

            handler.post(object : Runnable {
                @SuppressLint("NotifyDataSetChanged")
                override fun run() {
                    networkManager.getSoilHum()
                    networkManager.getTempAndHum()
                    handler.postDelayed(this, 10 * 1000)
                    if (ListForData.SoilHumList.size == 6 && ListForData.TempAndHumList.size == 4) {
                        ListForData.EverySoilHumDataList.add(
                            toAllSoilHumDataClass()
                        )
                        Log.d("CheckTag", ListForData.EverySoilHumDataList.toString())
                        myAdapter.setData(ListForData.EverySoilHumDataList)
                        myAdapter.notifyDataSetChanged()

                        apiListener?.onApiResponseReceived(Pair(ListForData.SoilHumList, ListForData.TempAndHumList))
                        saveEntryToDB(ListForData)

                        Log.d("MyLog",  "?????????????????? ?????????? ${ListForData.SoilHumList.toString()}")
                        Log.d("MyLog", "?????????????????? ?????????????? ${ListForData.TempAndHumList.toString()}")
                        ListForData.SoilHumList.clear()
                        ListForData.TempAndHumList.clear()

                    }

                }
            })

            Log.d("important", "Assigned: $uid")
        }
    var loggedIn = DEBUGMODE

    fun setApiListener(listener: ApiListener) {
        this.apiListener = listener
    }


    private fun saveEntryToDB(data: ListForData.Companion) {
        println(data.SoilHumList.toString())
        println(data.TempAndHumList.toString())

        var totalGreenhouseTemperature = 0f
        var totalGreenhouseHumidity = 0f
        for (entry in data.TempAndHumList) {
            totalGreenhouseTemperature += entry.temp.toFloat()
            totalGreenhouseHumidity += entry.hum.toFloat()
        }

        println("1: ${totalGreenhouseTemperature / data.TempAndHumList.size.toFloat()}")
        println("2: ${totalGreenhouseHumidity / data.TempAndHumList.size.toFloat()}")

        val avgTemp = decimalFormatter.format(totalGreenhouseTemperature / data.TempAndHumList.size.toFloat())
        val avgHumidity = decimalFormatter.format(totalGreenhouseHumidity / data.TempAndHumList.size.toFloat())


        println("3: $avgTemp")
        println("4: $avgHumidity")
        println("5: ${data.SoilHumList.size}")
        val soilHumListCopy = data.SoilHumList.toList()

        CoroutineScope(Dispatchers.IO).launch {
            val dataToSave = SensorData(
                createdAt = getCurrentDateTimeISO8601(),
                greenhouse_temperature = avgTemp.toFloat(),
                greenhouse_humidity = avgHumidity.toFloat(),
                furrow1_humidity = soilHumListCopy[0].humidity.toFloat(),
                furrow2_humidity = soilHumListCopy[1].humidity.toFloat(),
                furrow3_humidity = soilHumListCopy[2].humidity.toFloat(),
                furrow4_humidity = soilHumListCopy[3].humidity.toFloat(),
                furrow5_humidity = soilHumListCopy[4].humidity.toFloat(),
                furrow6_humidity = soilHumListCopy[5].humidity.toFloat()
            )

            println(dataToSave.toString())
            sensorDao.insertData(dataToSave)
        }

//        CoroutineScope(Dispatchers.IO).launch {
//            println(sensorDao.getSensorDataForDate("2023-03-11").toString())
//        }
    }

    override fun onCreate() {
        super.onCreate()
        AppSettingsManager.initContext(applicationContext)
        AppNotificationManager.initContext(applicationContext)

        if (AppSettingsManager.checkThatDataExists("tempValue") &&
                AppSettingsManager.checkThatDataExists("humValue")&&
                AppSettingsManager.checkThatDataExists("soilValue")) {
            AppSettingsManager.isAllBoundaryDataExists = true
        } else {
            AppSettingsManager.saveData("tempValue", "21")
            AppSettingsManager.saveData("humValue", "69.7")
            AppSettingsManager.saveData("soilValue", "67.4")
        }

        if (DEBUGMODE) {
            currentUID = "DEBUG"
        }
        createNotificationChannel()
    }

    private fun toAllSoilHumDataClass(): AllData{
        ListForData.SoilHumList.sortBy {
            it.id
        }
        ListForData.TempAndHumList.sortBy {
            it.id
        }
        return AllData(
            ListForData.SoilHumList[0].humidity,
            ListForData.SoilHumList[1].humidity,
            ListForData.SoilHumList[2].humidity,
            ListForData.SoilHumList[3].humidity,
            ListForData.SoilHumList[4].humidity,
            ListForData.SoilHumList[5].humidity,
            ListForData.TempAndHumList[0].temp,
            ListForData.TempAndHumList[1].temp,
            ListForData.TempAndHumList[2].temp,
            ListForData.TempAndHumList[3].temp,
            ListForData.TempAndHumList[0].hum,
            ListForData.TempAndHumList[1].hum,
            ListForData.TempAndHumList[2].hum,
            ListForData.TempAndHumList[3].hum,
        )
    }

    override fun onTerminate() {
//        Greenhouse.getInstance().saveState()
        super.onTerminate()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppNotificationManager.CHANNEL_ID,
                "greenhouse notification",
                NotificationManager.IMPORTANCE_HIGH
            )

            channel.description = "Some description of my notification"

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}