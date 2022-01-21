package com.coai.nuriserialporttester

import android.R
import android.content.*
import android.graphics.Color
import android.graphics.Rect
import android.os.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.lifecycle.ViewModelProvider
import com.coai.nuriserialporttester.databinding.ActivityMainBinding
import java.util.*
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.MotionEvent
import androidx.lifecycle.MutableLiveData
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    lateinit var mBinding: ActivityMainBinding
    lateinit var viewModel: MainViewModel
    var usbSerialService: UsbSerialService? = null
    var isSerialSevice = false
    val TAG = "태그"
    var autoThread = Thread()
    var chk_DataSize = false
    var port1_Recv_Count = 0
    var port2_Recv_Count = 0
    var port1_Send_Count = 0
    var port2_Send_Count = 0
    var port1_Error1_Count = 0
    var port1_Error2_Count = 0
    var port2_Error1_Count = 0
    var port2_Error2_Count = 0
    var aData: Int? = null
    var mData: Int? = null
    var usbSerialServiceIntent: Intent? = null

    @Volatile
    var autoRunning = 0

    private val serialServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UsbSerialService.UsbSerialServiceBinder
            usbSerialService = binder.getService()
            //핸들러 연결
            usbSerialService!!.setHandler(dataHandler)
            isSerialSevice = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isSerialSevice = false
            Toast.makeText(this@MainActivity, "서비스 연결 해제", Toast.LENGTH_SHORT).show();
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(mBinding.root)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        setSpinner()
        setButtonClickEvent()
        m_setDataSize()
        initCountView()

    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val focusView = currentFocus
        if (focusView != null) {
            val rect = Rect()
            focusView.getGlobalVisibleRect(rect)
            val x = ev.x.toInt()
            val y = ev.y.toInt()
            if (!rect.contains(x, y)) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm?.hideSoftInputFromWindow(focusView.windowToken, 0)
                focusView.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun bindSerialService() {
//        if (!UsbSerialService.SERVICE_CONNECTED){
//            val startSerialService = Intent(this, UsbSerialService::class.java)
//            startService(startSerialService)
//
//        }
        if (usbSerialServiceIntent == null) {
            Log.d(TAG, "바인드 시작")
            usbSerialServiceIntent = Intent(this, UsbSerialService::class.java)
            bindService(usbSerialServiceIntent, serialServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun initCountView() {
        port1_Recv_Count = 0
        port2_Recv_Count = 0
        port1_Send_Count = 0
        port2_Send_Count = 0
        port1_Error1_Count = 0
        port1_Error2_Count = 0
        port2_Error1_Count = 0
        port2_Error2_Count = 0
        mBinding.converter1RxTv.text = port1_Recv_Count.toString()
        mBinding.converter2RxTv.text = port2_Recv_Count.toString()
        mBinding.converter1TxTv.text = port1_Send_Count.toString()
        mBinding.converter2TxTv.text = port2_Send_Count.toString()
        mBinding.converter1ErrorTv.text = port1_Error1_Count.toString()
        mBinding.converter1Error2Tv.text = port1_Error2_Count.toString()
        mBinding.converter2ErrorTv.text = port2_Error1_Count.toString()
        mBinding.converter2Error2Tv.text = port2_Error2_Count.toString()
    }

    private val dataHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                UsbSerialService.SERIALPORT_CHK_CONNECTED -> {
                    addLogText("${msg.obj}")
                }
                UsbSerialService.PORT1_CHK_CONNECTED -> {
                    if (msg.arg1 == 1) {
                        addLogText("Port1 : ${usbSerialService?.usbSerialPort_1}, Baud Rate : ${msg.obj} 정상 연결되었습니다.")
                    } else {
                        addLogText("Port1이 연결되지 않았습니다.")
                    }
                }
                UsbSerialService.PORT2_CHK_CONNECTED -> {
                    if (msg.arg1 == 1) {
                        addLogText("Port2 : ${usbSerialService?.usbSerialPort_2}, Baud Rate : ${msg.obj} 정상 연결되었습니다.")
                    } else {
                        addLogText("Port2이 연결되지 않았습니다.")
                    }
                }
                UsbSerialService.RECEIVED_DATA -> {
                    if (msg.arg1 == 1) {
                        when (msg.arg2) {
                            1 -> {
                                port1_Recv_Count++
                                port1_Error1_Count++
                                mBinding.converter1ErrorTv.text = port1_Error1_Count.toString()
                                mBinding.converter1RxTv.text = port1_Recv_Count.toString()
                            }
                            2 -> {
                                port1_Error2_Count++
                                mBinding.converter1Error2Tv.text = port1_Error2_Count.toString()
                                mBinding.converter1RxTv.text = port1_Recv_Count.toString()
                            }
                            3 -> {
                                port1_Recv_Count++
                                mBinding.converter1RxTv.text = port1_Recv_Count.toString()
                            }
                        }
                    } else if (msg.arg1 == 2) {
                        when (msg.arg2) {
                            1 -> {
                                port2_Recv_Count++
                                port2_Error1_Count++
                                mBinding.converter2ErrorTv.text = port2_Error1_Count.toString()
                                mBinding.converter2RxTv.text = port2_Recv_Count.toString()
                            }
                            2 -> {
                                port2_Error2_Count++
                                mBinding.converter2Error2Tv.text = port2_Error2_Count.toString()
                                mBinding.converter2RxTv.text = port2_Recv_Count.toString()
                            }
                            3 -> {
                                port2_Recv_Count++
                                mBinding.converter2RxTv.text = port2_Recv_Count.toString()
                            }
                        }
                    }
                }
                UsbSerialService.SENDED_DATA -> {
                    if (msg.arg1 == 1) {
                        port1_Send_Count++
                        mBinding.converter1TxTv.text = port1_Send_Count.toString()
                    } else if (msg.arg1 == 2) {
                        port2_Send_Count++
                        mBinding.converter2TxTv.text = port2_Send_Count.toString()
                    }
                }
                5 -> {
                    if (msg.obj == 1) {
                        usbSerialService?.isRunning = false
                        first_resultAlert()
                    } else if (msg.obj == 2) {
                        usbSerialService?.isRunning = false
                        autoRunning = 1

                    } else if (msg.obj == 3) {
                        autoRunning = 1
                        usbSerialService?.isRunning = false
//                        usbSerialService?.port1_SendThread = Thread()
//                        usbSerialService?.port1_ReceivedThread = Thread()
//                        usbSerialService?.port2_SendThread = Thread()
//                        usbSerialService?.port2_ReceivedThread = Thread()
                        second_resultAlert()
                    }
                }
            }
            super.handleMessage(msg)
        }
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
        bindSerialService()
    }

    override fun onDestroy() {
        unbindService(serialServiceConnection)
        autoThread.interrupt()
        super.onDestroy()
    }


    private fun setSpinner() {
        val arrayAdapter = ArrayAdapter(
            this,
            R.layout.simple_spinner_dropdown_item,
            MainViewModel.LIST_OF_BAUD_RATE
        )
        mBinding.spinnerBaudrate.adapter = arrayAdapter

        mBinding.spinnerBaudrate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                //화면 회전시 AdapterView.OnItemSelectedListener 에러 발생
                //view가 nonNull 로 되어있는데 nullable으로 변환
                override fun onItemSelected(
                    adapterView: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {//스피너가 선택 되었을때
                    val selectedBR = MainViewModel.LIST_OF_BAUD_RATE[position]
//                    val selectWM = MainViewModel.WAIT_MILLIS[position]
//                    usbSerialService?.setWaitMillis(selectWM)
                    addLogText("Baud Rate : " + selectedBR + "선택했습니다.")
                    usbSerialService?.getBuadRate(selectedBR)
                    if (usbSerialService?.checkUsbDevice() == true) {
                        usbSerialService?.serialDisconnect()
                        if (usbSerialService?.checkSerialPort() == false) {
                            usbSerialService?.serialPortConnect(selectedBR)

                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "시리얼포트를 연결해주세요.",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                        addLogText("시리얼포트를 연결해주세요.")
                    }

                }

                override fun onNothingSelected(adapterView: AdapterView<*>) {
                    Toast.makeText(this@MainActivity, "Baud Rate를 선택해주세요.", Toast.LENGTH_SHORT)
                        .show()
                }
            }

    }

    private fun setButtonClickEvent() {
        mBinding.btnAutoRun.setOnClickListener {
            onClick(mBinding.btnAutoRun)
        }
//        mBinding.btnAutoStop.setOnClickListener {
//            onClick(mBinding.btnAutoStop)
//        }
        mBinding.btnSendData.setOnClickListener {
            onClick(mBinding.btnSendData)
        }
        mBinding.btnSendCancel.setOnClickListener {
            onClick(mBinding.btnSendCancel)
        }
        mBinding.cbBoth.setOnClickListener {
            onClick(mBinding.cbBoth)
        }
        mBinding.cb1to2.setOnClickListener {
            onClick(mBinding.cb1to2)
        }
        mBinding.cb2to1.setOnClickListener {
            onClick(mBinding.cb2to1)
        }
        mBinding.btnLogViewClear.setOnClickListener {
            onClick(mBinding.btnLogViewClear)
        }
        mBinding.btnCountClear.setOnClickListener {
            onClick(mBinding.btnCountClear)
        }
    }

    val CHK = "체크"
    private fun onClick(view: View) {
        when (view) {
            mBinding.btnAutoRun -> {
                if (usbSerialService?.checkSerialPort() == true) {
                    if (a_setDataSize()) {
                        step1_autoTestStart()
                        addLogText("자동 테스트를 시작 합니다.")

                    }
                } else {
                    addLogText("시리얼 포트를 연결해 주세요.")
                }
//                step3_autoTestStart(MainViewModel.LIST_OF_BAUD_RATE[6])
            }
//            mBinding.btnAutoStop -> {
//                autoFlag = false
//                mBinding.btnAutoRun.isEnabled = true
//            }
            mBinding.btnSendData -> {
                disableEnableControls(false, mBinding.autoLayer)
                if (usbSerialService?.checkSerialPort() == true) {
                    if (m_setDataSize()) {
                        if (mBinding.cbBoth.isChecked || mBinding.cb1to2.isChecked || mBinding.cb2to1.isChecked) {
                            mBinding.spinnerBaudrate.isEnabled = false
                            mBinding.etDataSize.isEnabled = false
                            mBinding.btnSendData.isEnabled = false
                            val datasize: Double = mBinding.etDataSize.text.toString().toDouble()
                            val baudrate: Double =
                                mBinding.spinnerBaudrate.selectedItem.toString().toDouble()
                            val temp: Double = (1000 / (baudrate / (10 * datasize)) * 2)
                            val timeout = Math.ceil(temp).toInt()
                            usbSerialService?.setTimeOut(timeout)
                            addLogText("수동 테스트를 시작 합니다.")

                            if (mBinding.cbBoth.isChecked) {
                                usbSerialService?.isRunning = true
                                val parser = SerialProtocol(mData!!)
                                val initialData = parser.BuzzerOn(1.toByte())
                                Log.d(CHK, "setdata = ${initialData?.toHex()}")
                                Log.d(CHK, "setdata Size = ${initialData?.size}")
                                usbSerialService?.usbSerialPort_2?.purgeHwBuffers(true, true)
                                usbSerialService?.usbSerialPort_1?.purgeHwBuffers(true, true)

                                usbSerialService?.port1_SendData(initialData)
                                usbSerialService?.port2ReceivedThread()
                                usbSerialService?.port2_SendData(initialData)
                                usbSerialService?.port1ReceivedThread()

                                usbSerialService?.port1_ReceivedThread?.start()
                                usbSerialService?.port2_ReceivedThread?.start()
                                usbSerialService?.port1_SendThread?.start()
                                usbSerialService?.port2_SendThread?.start()
                            } else if (mBinding.cb1to2.isChecked) {
                                val parser = SerialProtocol(mData!!)
                                val initialData = parser.BuzzerOn(1.toByte())
                                usbSerialService?.isRunning = true
                                usbSerialService?.singleTest(initialData, 1)
                                usbSerialService?.port1_SendThread?.start()
                                usbSerialService?.port2_ReceivedThread?.start()

                            } else if (mBinding.cb2to1.isChecked) {
                                val parser = SerialProtocol(mData!!)
                                val initialData = parser.BuzzerOn(1.toByte())
                                usbSerialService?.isRunning = true
                                usbSerialService?.singleTest(initialData, 2)
                                usbSerialService?.port2_SendThread?.start()
                                usbSerialService?.port1_ReceivedThread?.start()
                            }
                        } else {
                            addLogText("모드를 선택 해주세요.")
                        }
                    }
                } else {
                    addLogText("시리얼 포트를 연결해 주세요.")
                }
            }
            mBinding.btnSendCancel -> {
                usbSerialService?.cancelSend()
                disableEnableControls(true, mBinding.autoLayer)
                mBinding.spinnerBaudrate.isEnabled = true
                mBinding.etDataSize.isEnabled = true
                mBinding.btnSendData.isEnabled = true
                addLogText("테스트를 중지 하였습니다.")
            }
            mBinding.cbBoth -> {
                if (usbSerialService?.isRunning == false) {
                    if (mBinding.cbBoth.isChecked) {
                        initCountView()
                        mBinding.cb1to2.isChecked = false
                        mBinding.cb2to1.isChecked = false
                    }
                } else {
                    mBinding.cbBoth.isChecked = false
                    Toast.makeText(this, "테스트 중 모드를 변경할수 없습니다.", Toast.LENGTH_SHORT).show()
                    addLogText("테스트 중 모드를 변경할수 없습니다.")
                }
            }
            mBinding.cb1to2 -> {
                if (usbSerialService?.isRunning == false) {
                    if (mBinding.cb1to2.isChecked) {
                        initCountView()
                        mBinding.cbBoth.isChecked = false
                        mBinding.cb2to1.isChecked = false
                    }
                } else {
                    mBinding.cb1to2.isChecked = false
                    Toast.makeText(this, "테스트 중 모드를 변경할수 없습니다.", Toast.LENGTH_SHORT).show()
                    addLogText("테스트 중 모드를 변경할수 없습니다.")
                }
            }
            mBinding.cb2to1 -> {
                if (usbSerialService?.isRunning == false) {
                    if (mBinding.cb2to1.isChecked) {
                        initCountView()
                        mBinding.cb1to2.isChecked = false
                        mBinding.cbBoth.isChecked = false
                    }
                } else {
                    mBinding.cb2to1.isChecked = false
                    Toast.makeText(this, "테스트 중 모드를 변경할수 없습니다.", Toast.LENGTH_SHORT).show()
                    addLogText("테스트 중 모드를 변경할수 없습니다.")
                }
            }
            mBinding.btnLogViewClear -> {
                mBinding.layoutLogViewer.removeAllViews()
            }
            mBinding.btnCountClear -> {
                initCountView()
            }
        }
    }

    private fun m_setDataSize(): Boolean {
        var ret = false
        if (mBinding.etDataSize.text.isNullOrEmpty()) {
            addLogText("데이터 크기를 입력해주세요.")
        } else {
            val size: Int = Integer.parseInt(mBinding.etDataSize.text.toString())
            if (size < 7 || size > 100) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Data Size")
                builder.setMessage("데이터 사이즈 크기는\n 최소:7, 최대:100 입니다.")
                builder.show()
                addLogText("데이터 사이즈 크기는\n최소:7, 최대:100 입니다.")
            } else {
                mData = size
                usbSerialService?.getDataSize(mData!!)
                ret = true
            }
        }
        return ret
    }

    private fun a_setDataSize(): Boolean {
        var ret = false
        if (mBinding.etAutoDataSize.text.isNullOrEmpty()) {
            addLogText("데이터 크기를 입력해주세요.")
        } else {
            val size: Int = Integer.parseInt(mBinding.etAutoDataSize.text.toString())
            if (size < 7 || size > 100) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Data Size")
                builder.setMessage("데이터 사이즈 크기는\n 최소:7, 최대:100 입니다.")
                builder.show()
                addLogText("데이터 사이즈 크기는\n최소:7, 최대:100 입니다.")
            } else {
                aData = size
                usbSerialService?.getDataSize(aData!!)
                ret = true
            }
        }
        return ret
    }

    fun ByteArray.toHex(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun disableEnableControls(enable: Boolean, vg: ViewGroup) {
        for (i in 0 until vg.childCount) {
            val child = vg.getChildAt(i)
            child.isEnabled = enable
            if (child is ViewGroup) {
                disableEnableControls(enable, child)
            }
        }
    }

    private fun step2_autoTestStart() {
        initCountView()
        if (a_setDataSize()) {
            val data = aData
            disableEnableControls(false, mBinding.manualLayer)
            mBinding.btnAutoRun.isEnabled = false

            autoThread = Thread {
                for (i in MainViewModel.LIST_OF_BAUD_RATE.reversedArray()) {
                    if (usbSerialService?.usbSerialPort_1 != null && usbSerialService?.usbSerialPort_2 != null) {
                        usbSerialService?.isRunning = false
                        usbSerialService?.serialDisconnect()
                    }
//                Log.d(TAG, "포트 OPEN LIST_OF_BAUD_RATE : $i")
//                    val selectWM = MainViewModel.WAIT_MILLIS.reversedArray()[num]
//                    num++
//                    usbSerialService?.setWaitMillis(selectWM)
                    val baudrate: Double = i.toString().toDouble()
                    val temp: Double = (1000 / (baudrate / (10 * data!!)) * 2)
                    val timeout = Math.ceil(temp).toInt()
                    usbSerialService?.setTimeOut(timeout)

                    usbSerialService?.isRunning = true
                    usbSerialService?.getDataSize(data)
                    usbSerialService?.serialPortConnect(i)
                    usbSerialService?.usbSerialPort_2?.purgeHwBuffers(true, true)
                    usbSerialService?.usbSerialPort_1?.purgeHwBuffers(true, true)
                    val startTime: Long = System.currentTimeMillis()
                    val parser = SerialProtocol(data)
                    val initialData = parser.BuzzerOn(1.toByte())

                    usbSerialService?.port1_SendData(initialData)
                    usbSerialService?.port2ReceivedThread()
                    usbSerialService?.port2_SendData(initialData)
                    usbSerialService?.port1ReceivedThread()
                    usbSerialService?.port1_ReceivedThread?.start()
                    usbSerialService?.port2_ReceivedThread?.start()
                    usbSerialService?.port1_SendThread?.start()
                    usbSerialService?.port2_SendThread?.start()

                    while (true) {
                        if (startTime + 30000 < System.currentTimeMillis()) {
                            usbSerialService?.isRunning = false
                        }
                        if (startTime + 31000 < System.currentTimeMillis()) {
                            break
                        }
                    }

                }
                runOnUiThread {
                    last_resultAlert()
                    disableEnableControls(true, mBinding.manualLayer)
                    disableEnableControls(true, mBinding.autoLayer)
                }
            }
            autoThread.start()

        }
    }

    var autoFlag = false

    private fun step1_autoTestStart() {
        initCountView()
        mBinding.layoutLogViewer.removeAllViews()
        if (a_setDataSize()) {
            val data = aData
            disableEnableControls(false, mBinding.manualLayer)
            mBinding.btnAutoRun.isEnabled = false
            autoThread = Thread {
                for (i in MainViewModel.LIST_OF_BAUD_RATE.reversedArray()) {
                    if (usbSerialService?.usbSerialPort_1 != null && usbSerialService?.usbSerialPort_2 != null) {
                        usbSerialService?.isRunning = false
                        usbSerialService?.serialDisconnect()
                    }

                    val baudrate: Double = i.toString().toDouble()
                    val temp: Double = (1000 / (baudrate / (10 * data!!)) * 2)
                    val timeout = Math.ceil(temp).toInt()
                    usbSerialService?.setTimeOut(timeout)

                    usbSerialService?.isRunning = true
                    usbSerialService?.getDataSize(data)
                    usbSerialService?.serialPortConnect(i)
                    usbSerialService?.usbSerialPort_2?.purgeHwBuffers(true, true)
                    usbSerialService?.usbSerialPort_1?.purgeHwBuffers(true, true)
                    val parser = SerialProtocol(data)
                    val initialData = parser.BuzzerOn(1.toByte())

                    usbSerialService?.port1_AutoSendData(initialData, i)
                    usbSerialService?.port2_AutoSendData(initialData, i)
                    usbSerialService?.port2_AutoReceivedThread()
                    usbSerialService?.port1_AutoReceivedThread()

                    usbSerialService?.port1_SendThread?.start()
                    usbSerialService?.port2_SendThread?.start()
                    usbSerialService?.port1_ReceivedThread?.start()
                    usbSerialService?.port2_ReceivedThread?.start()
                    while (true) {
                        if (autoRunning == 1) {
                            break
                        }
                        Thread.sleep(1)
                    }
                    autoRunning = 0
                    Thread.sleep(500)
                }

            }
            autoThread.start()

        }
    }


    fun addLogText(text: String?) {
        mBinding.layoutLogViewer.post(Runnable {
            val addTextView = TextView(this)
            addTextView.text = text
            addTextView.textSize = 12.toFloat()
            addTextView.setTextColor(Color.BLACK)
            mBinding.layoutLogViewer.addView(addTextView)
            mBinding.scrollView.scrollTo(0, mBinding.layoutLogViewer.bottom + 500)
            mBinding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        })
    }

    private fun first_resultAlert() {
        val loss_result1: Double =
            (port1_Error2_Count.toDouble()) / port1_Recv_Count.toDouble() * 100
        val loss_result2: Double =
            (port2_Error2_Count.toDouble()) / port2_Recv_Count.toDouble() * 100


        var result1: String = ""
        var result2: String = ""
        loss_result1.apply {
            if (this >= 0 && this < 5) {
                addLogText("port1은 최소 E급 입니다.")
                result1 = "port1은 최소 E급 입니다."
            } else if (this >= 5) {
                addLogText("port1은 F급 입니다.")
                result1 = "port1은 F급 입니다."
            }
        }

        loss_result2.apply {
            if (this >= 0 && this < 5) {
                addLogText("port2은 최소 E급 입니다.")
                result2 = "port2은 최소 E급 입니다."
            } else if (this >= 5) {
                addLogText("port2은 F급 입니다.")
                result2 = "port2은 F급 입니다."
            }
        }
        build_firstResultDialog(result1, result2)
    }

    private fun second_resultAlert() {
        val loss_result1: Double =
            (port1_Error2_Count.toDouble()) / port1_Recv_Count.toDouble() * 100
        val loss_result2: Double =
            (port2_Error2_Count.toDouble()) / port2_Recv_Count.toDouble() * 100


        var result1: String = ""
        var result2: String = ""
        loss_result1.apply {
            if (port1_Error1_Count > 0) {
                addLogText("port1은 E급 입니다.")
                result1 = "port1은 E급 입니다."
            } else if (this >= 0 && this < 5) {
                addLogText("port1은 최소 B급 입니다.")
                result1 = "port1은 최소 B급 입니다."
            }
        }

        loss_result2.apply {
            if (port2_Error1_Count > 0) {
                addLogText("port2은 E급 입니다.")
                result2 = "port2은 E급 입니다."
            } else if (this >= 0 && this < 5) {
                addLogText("port2은 최소 B급 입니다.")
                result2 = "port2은 최소 B급 입니다."
            }
        }

        build_secondResultDialog(result1, result2)
    }

    private fun last_resultAlert() {
        val loss_result1: Double =
            (port1_Error2_Count.toDouble()) / port1_Recv_Count.toDouble() * 100
        val loss_result2: Double =
            (port2_Error2_Count.toDouble()) / port2_Recv_Count.toDouble() * 100


        var result1: String = ""
        var result2: String = ""
        loss_result1.apply {
            if (port1_Error1_Count > 0) {
                addLogText("port1은 E급 입니다.")
                result1 = "port1은 E급 입니다."
            } else if (this >= 1 && this < 5) {
                addLogText("port1은 B급 입니다.")
                result1 = "port1은 B급 입니다."
            } else if (this >= 0 && this < 1) {
                addLogText("port1은 A급 입니다.")
                result1 = "port1은 A급 입니다."
            }
        }

        loss_result2.apply {
            if (port1_Error1_Count > 0) {
                addLogText("port2은 E급 입니다.")
                result2 = "port2은 E급 입니다."
            } else if (this >= 1 && this < 5) {
                addLogText("port2은 B급 입니다.")
                result2 = "port2은 B급 입니다."
            } else if (this >= 0 && this < 1) {
                addLogText("port2은 A급 입니다.")
                result2 = "port2은 A급 입니다."
            }
        }

        build_lastResultDialog(result1, result2)
    }

    private fun build_firstResultDialog(result1: String, result2: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Test Result")
        builder.setMessage("$result1\n$result2")
        builder.setPositiveButton("다음 테스트 진행하기", object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                autoRunning = 1
            }
        })
//        builder.setNegativeButton("취소", object : DialogInterface.OnClickListener {
//            override fun onClick(dialog: DialogInterface?, which: Int) {
//                dialog?.cancel()
//                disableEnableControls(true, mBinding.manualLayer)
//                disableEnableControls(true, mBinding.autoLayer)
//            }
//        })
        builder.setCancelable(false)
        builder.show()
    }

    private fun build_secondResultDialog(result1: String, result2: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Test Result")
        builder.setMessage("$result1\n$result2")
        builder.setPositiveButton("다음 테스트 진행하기", object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                step2_autoTestStart()
            }
        })
        builder.setNegativeButton("취소", object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                dialog?.cancel()
                disableEnableControls(true, mBinding.manualLayer)
                disableEnableControls(true, mBinding.autoLayer)
            }
        })
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()


    }

    private fun build_lastResultDialog(result1: String, result2: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Test Result")
        builder.setMessage("$result1\n$result2")

        builder.setNegativeButton("취소", object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                dialog?.cancel()
            }
        })
        builder.show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigationBar()
        }
    }

    private fun hideNavigationBar() {
        window.decorView.apply {
            // Hide both the navigation bar and the status bar.
            // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
            // a general rule, you should design your app to hide the status bar whenever you
            // hide the navigation bar.
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                        View.STATUS_BAR_VISIBLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
//                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN


        }
//        window.navigationBarColor = Color.parseColor("#FF0000")
    }


}
