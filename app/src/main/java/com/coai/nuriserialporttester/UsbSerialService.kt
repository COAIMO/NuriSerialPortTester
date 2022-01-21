package com.coai.nuriserialporttester

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.*
import android.util.Log
import android.widget.Toast
import com.coai.nuriserialporttester.BuildConfig
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.*

class UsbSerialService : Service() {
    companion object {
        const val ACTION_USB_READY = "UsbSerialService.USB_READY"
        const val ACTION_USB_NOT_SUPPORTED = "UsbSerialService.USB_NOT_SUPPORTED"
        const val ACTION_NO_USB = "UsbSerialService.NO_USB"
        const val ACTION_USB_PERMISSION_GRANTED = "UsbSerialService.USB_PERMISSION_GRANTED"
        const val ACTION_USB_PERMISSION_NOT_GRANTED = "UsbSerialService.USB_PERMISSION_NOT_GRANTED"
        const val ACTION_USB_DISCONNECTED = "UsbSerialService.USB_DISCONNECTED"
        const val ACTION_CDC_DRIVER_NOT_WORKING = "UsbSerialService.ACTION_CDC_DRIVER_NOT_WORKING"
        const val ACTION_USB_DEVICE_NOT_WORKING = "UsbSerialService.ACTION_USB_DEVICE_NOT_WORKING"
        private const val ACTION_USB_PERMISSION_1 = BuildConfig.APPLICATION_ID + ".GRANT_USB_1"
        private const val ACTION_USB_PERMISSION_2 = BuildConfig.APPLICATION_ID + ".GRANT_USB_2"
        const val SERIALPORT_CHK_CONNECTED = 0
        const val PORT1_CHK_CONNECTED = 1
        const val PORT2_CHK_CONNECTED = 2
        const val RECEIVED_DATA = 3
        const val SENDED_DATA = 4
        const val WAIT_MILLIS = 200
    }

    val binder = UsbSerialServiceBinder()
    var usbManager: UsbManager? = null
    var usbDriver_1: UsbSerialDriver? = null
    var usbDriver_2: UsbSerialDriver? = null
    var usbDevice_1: UsbDevice? = null
    var usbDevice_2: UsbDevice? = null
    var usbSerialPort_1: UsbSerialPort? = null
    var usbSerialPort_2: UsbSerialPort? = null
    var usbConnection_1: UsbDeviceConnection? = null
    var usbConnection_2: UsbDeviceConnection? = null
    private var usbIoManager_1: SerialInputOutputManager? = null
    private var usbIoManager_2: SerialInputOutputManager? = null

    var usbDrivers: List<UsbSerialDriver>? = null
    private var serialPort_1Connected = false
    private var serialPort_2Connected = false
    private var isUsbDevice_1 = false
    private var isUsbDevice_2 = false
    var mHandler = Handler()
    val TAG = "태그"
    lateinit var port1_SendThread: Thread
    lateinit var port2_SendThread: Thread
    lateinit var port2_ReceivedThread: Thread
    lateinit var port1_ReceivedThread: Thread
    var isRunning = false
    val port1_SendSync = RXTXSynchronized()
    val port2_SendSync = RXTXSynchronized()
    val sync_Com = RXTXSynchronized()

    private val PREFIX: ByteArray = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    val CHK = "체크"

    inner class UsbSerialServiceBinder : Binder() {
        fun getService(): UsbSerialService {
            return this@UsbSerialService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        Log.d(TAG, "UsbService : onCreate")
        setFilter()
        findUSBSerialDevice()
        super.onCreate()
    }

    override fun onDestroy() {
        Log.d(TAG, "UsbService : onDestroy")
        unregisterReceiver(usbReceiver)
        disconnect()
        super.onDestroy()
    }

    val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION_1.equals(intent?.action)) {
                val granted: Boolean =
                    intent?.getExtras()!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) {
                    mHandler.obtainMessage(
                        SERIALPORT_CHK_CONNECTED,
                        "USB_Serial_Device1 접근을 허용하였습니다."
                    ).sendToTarget()

                    usbDriver_2 = usbDrivers!![1]
                    usbDevice_2 = usbDriver_2?.device
                    isUsbDevice_2 = true
                    if (!usbManager!!.hasPermission(usbDevice_2)) {
                        val intent2: PendingIntent =
                            PendingIntent.getBroadcast(
                                context,
                                0,
                                Intent(ACTION_USB_PERMISSION_2),
                                0
                            )
                        usbManager?.requestPermission(usbDevice_2, intent2)
                    } else {
                        mBaud_rate?.let { serialPortConnect(it) }
                    }
                    mHandler.obtainMessage(SERIALPORT_CHK_CONNECTED, "시리얼 포트 2개가 연결되었습니다.")
                        .sendToTarget()

                } else {
                    mHandler.obtainMessage(
                        SERIALPORT_CHK_CONNECTED,
                        "USB_Serial_Device1 시리얼 접근을 허용하지 않았습니다."
                    ).sendToTarget()
                }
            } else if (ACTION_USB_PERMISSION_2.equals(intent?.action)) {
                val granted: Boolean =
                    intent?.getExtras()!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) {
                    mHandler.obtainMessage(
                        SERIALPORT_CHK_CONNECTED,
                        "USB_Serial_Device2 접근을 허용하였습니다."
                    ).sendToTarget()
                    mBaud_rate?.let { serialPortConnect(it) }
                } else {
                    mHandler.obtainMessage(
                        SERIALPORT_CHK_CONNECTED,
                        "USB_Serial_Device2 시리얼 접근을 허용하지 않았습니다."
                    ).sendToTarget()
                }
            } else if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                Handler().postDelayed(Runnable {
                    findUSBSerialDevice()
                }, 1000)
            } else if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                disconnect()
                mHandler.obtainMessage(
                    SERIALPORT_CHK_CONNECTED,
                    "USB 연결이 끊겼습니다."
                ).sendToTarget()
            }
        }
    }

    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION_1)
        filter.addAction(ACTION_USB_PERMISSION_2)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun findUSBSerialDevice() {
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager!!.deviceList.isEmpty()) {
            Log.d(TAG, "connection failed: device not found")
            Toast.makeText(this, "connection failed: device not found", Toast.LENGTH_SHORT).show()
        }
        usbDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (usbDrivers == null) {
            Log.d(TAG, "connection failed: no driver for device")
            Toast.makeText(this, "connection failed: no driver for device", Toast.LENGTH_SHORT)
                .show()
        }

        //resume에서 타이머를 돌려서 2개가 될때까지 처리
        if (usbDrivers?.size!! == 2) {
//            Log.d(TAG, "connection failed: only one driver)
            usbDriver_1 = usbDrivers!![0]
            usbDevice_1 = usbDriver_1!!.device
            isUsbDevice_1 = true
            if (!usbManager!!.hasPermission(usbDevice_1)) {
                val intent: PendingIntent =
                    PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION_1), 0)
                usbManager?.requestPermission(usbDevice_1, intent)
            } else {
                usbDriver_2 = usbDrivers!![1]
                usbDevice_2 = usbDriver_2?.device
                isUsbDevice_2 = true
                if (!usbManager!!.hasPermission(usbDevice_2)) {
                    val intent2: PendingIntent =
                        PendingIntent.getBroadcast(
                            this,
                            0,
                            Intent(ACTION_USB_PERMISSION_2),
                            0
                        )
                    usbManager?.requestPermission(usbDevice_2, intent2)
                } else {
                    mBaud_rate?.let { serialPortConnect(it) }
                }
            }
            mHandler.obtainMessage(
                SERIALPORT_CHK_CONNECTED,
                "시리얼 포트를 1개만 연결되었습니다. 시리얼 포트를 1개 더 연결해주세요."
            ).sendToTarget()

            if (usbDrivers?.size == 3) {
                Toast.makeText(this, "시리얼 포트 3개가 연결되어있습니다.\n 1개를 제거 해주세요.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun checkUsbDevice(): Boolean {
        var ret = false
        if (isUsbDevice_1 && isUsbDevice_2) {
            ret = true
        }
        return ret
    }

    private var mBaud_rate: Int? = null
    fun getBuadRate(buadrate: Int) {
        mBaud_rate = buadrate
    }

    private var mDataSize: Int? = null
    fun getDataSize(size: Int) {
        mDataSize = size
    }

    var time_out: Int? = null
    fun setTimeOut(millis: Int) {
        if (millis < 20) {
            time_out = 20
        } else {
            time_out = millis

        }
    }

    fun serialPortConnect(baud_rate: Int) {
        if (usbManager != null) {
            if (usbManager!!.hasPermission(usbDevice_1) && usbSerialPort_1 == null) {
                usbConnection_1 = usbManager!!.openDevice(usbDevice_1)
                usbSerialPort_1 = usbDriver_1!!.ports[0]
                usbSerialPort_1!!.open(usbConnection_1)
                usbSerialPort_1!!.setParameters(
                    baud_rate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                serialPort_1Connected = true
                val msg: Message = mHandler.obtainMessage().apply {
                    what = PORT1_CHK_CONNECTED
                    arg1 = 1
                    obj = baud_rate
                }
                mHandler.sendMessage(msg)
            } else {
                val msg: Message = mHandler.obtainMessage().apply {
                    what = PORT1_CHK_CONNECTED
                    arg1 = 2
                }
                mHandler.sendMessage(msg)
            }

            if (usbManager!!.hasPermission(usbDevice_2) && usbSerialPort_2 == null) {

                usbConnection_2 = usbManager!!.openDevice(usbDevice_2)
                usbSerialPort_2 = usbDriver_2!!.ports[0]
                usbSerialPort_2!!.open(usbConnection_2)
                usbSerialPort_2!!.setParameters(
                    baud_rate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                serialPort_2Connected = true
                val msg: Message = mHandler.obtainMessage().apply {
                    what = PORT2_CHK_CONNECTED
                    arg1 = 1
                    obj = baud_rate
                }
                mHandler.sendMessage(msg)
            } else {
                val msg: Message = mHandler.obtainMessage().apply {
                    what = PORT2_CHK_CONNECTED
                    arg1 = 2
                }
                mHandler.sendMessage(msg)
            }
        } else if (usbManager == null) {
            mHandler.obtainMessage(SERIALPORT_CHK_CONNECTED, "시리얼 포트를 연결해 주세요.").sendToTarget()
        }

    }

    fun checkSerialPort(): Boolean {
        var ret = false
        if (serialPort_1Connected && serialPort_2Connected) {
            ret = true
        }
        return ret
    }

    fun serialDisconnect() {
        isRunning = false
        try {
            usbSerialPort_1?.close()
            usbSerialPort_2?.close()

        } catch (ignored: IOException) {
        }
        usbSerialPort_1 = null
        usbSerialPort_2 = null
        serialPort_2Connected = false
        serialPort_1Connected = false
    }

    private fun disconnect() {
        isRunning = false
        serialPort_1Connected = false
        serialPort_2Connected = false
        usbIoManager_1 = null
        usbIoManager_2 = null
        port1_SendThread = Thread()
        port2_SendThread = Thread()
        port1_ReceivedThread = Thread()
        port2_ReceivedThread = Thread()
        usbDrivers = null
        try {
            usbSerialPort_1?.close()
            usbSerialPort_2?.close()

        } catch (ignored: IOException) {
        }
        usbSerialPort_1 = null
        usbSerialPort_2 = null

    }

    //activity랑 연결해줄 핸들러 셋 fun
    fun setHandler(mHandler: Handler) {
        this.mHandler = mHandler
    }

    fun port1_SendData(data: ByteArray?) {
        syncFlag1 = 1
        port1_SendThread = Thread {
            while (isRunning) {
                try {
                    usbSerialPort_1?.write(data, WAIT_MILLIS)
                    Log.d(CHK, "port1_SendData = ${data?.toHex()}")
                    val msg: Message = mHandler.obtainMessage().apply {
                        what = SENDED_DATA
                        arg1 = 1
                        arg2 = 1
                    }
                    mHandler.sendMessage(msg)
                    port1_SendSync.waitOne(500)
                } catch (e: InterruptedException) {
                    val msg: Message = mHandler.obtainMessage().apply {
                        what = SENDED_DATA
                        arg1 = 1
                        arg2 = 2
                    }
                    mHandler.sendMessage(msg)

                }
            }
        }
    }

    fun port2ReceivedThread() {
        if (usbSerialPort_2 == null) return
        val parser = SerialProtocol(mDataSize!!)
        val dataSize = mDataSize!!
        val buffRecvProtocol = ByteArray(1024)
        var idxRecv = 0
        var startRecv: Long = System.currentTimeMillis()
        val buff = ByteArray(1024)
        port2_ReceivedThread = Thread {
            if (syncFlag1 == 1) {
                syncFlag1 = 0
                while (isRunning) {
                    try {
                        val cnt = usbSerialPort_2!!.read(buff, WAIT_MILLIS)
                        //수신 데이터크기가 같거나 buff 내부에 존재 시
                        if (cnt > 0) {
                            //1. 버퍼 프로토콜로 데이터 이동
                            buff.copyInto(buffRecvProtocol, idxRecv, 0, cnt)

                            // 2. 버퍼 프로토콜 인덱스 변경
                            if (idxRecv == 0)
                                startRecv = System.currentTimeMillis()
                            //                    Log.d(TAG, "RECV DATA2 ====== ${buff.take(cnt).toByteArray().toHex()}")
                            idxRecv += cnt
                            while (true) {
                                val chkpos = indexOfBytes(buffRecvProtocol, 0, idxRecv)
                                if (chkpos != -1) {
                                    //헤더 유무 체크 및 헤더 몇번째있는지 반환
                                    //                            Log.d(TAG, "$chkpos")
                                    val scndpos =
                                        indexOfBytes(buffRecvProtocol, chkpos + 1, idxRecv - chkpos)
                                    // 다음 데이터가 없을 경우 -1 반환 (헤더 중복체크)
                                    if (scndpos == -1) {
                                        if (idxRecv - chkpos == dataSize) {
                                            Log.d(CHK, "re2 dataSize: $dataSize")
                                            val recvdata = ByteArray(dataSize)
                                            buffRecvProtocol.copyInto(recvdata, 0, chkpos, idxRecv)
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                    .sendToTarget()
                                            } else {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 3)
                                                    .sendToTarget()
                                            }
                                            idxRecv = 0
                                            port2_SendSync.set()
                                        } else if (idxRecv - chkpos > dataSize) {
                                            Log.d(CHK, "re2 dataSize: $dataSize")
                                            val recvdata = ByteArray(dataSize)
                                            buffRecvProtocol.copyInto(
                                                recvdata,
                                                0,
                                                chkpos,
                                                chkpos + dataSize
                                            )
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                    .sendToTarget()
                                            }

                                            idxRecv = 0
                                            // 쓰레기 데이터 에러 추가
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                .sendToTarget()

                                            port2_SendSync.set()
                                        } else {

                                            //타임아웃
                                            if (isRunning) {

                                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                                        .sendToTarget()
                                                }
                                            }
                                            break
                                        }
                                    } else {
                                        // 중복 데이터가 있는 경우
                                        val recvdata = ByteArray(scndpos - chkpos)
                                        buffRecvProtocol.copyInto(recvdata, 0, chkpos, scndpos)
                                        if (!parser.parse(recvdata)) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                .sendToTarget()
                                        } else {
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 3)
                                                .sendToTarget()
                                        }

                                        // 1-3-2. 수신 프로토콜 데이터 당기기고 인덱스 수정
                                        val tmp = idxRecv - scndpos
                                        //                                Log.d(TAG, "idxRecv : $idxRecv dataSize : $dataSize")
                                        val tempbuff = ByteArray(tmp)
                                        buffRecvProtocol.copyInto(
                                            tempbuff,
                                            0,
                                            scndpos,
                                            scndpos + tmp
                                        )
                                        tempbuff.copyInto(buffRecvProtocol, 0, 0, endIndex = tmp)
                                        idxRecv = tmp
                                        //                                Log.d(TAG, "port2ReceivedThread-set2")
                                        port2_SendSync.set()
                                    }

                                } else {
                                    //타입아웃
                                    if (isRunning) {

                                        if (startRecv + time_out!! < System.currentTimeMillis()) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                                .sendToTarget()
                                        }
                                    }
                                    break
                                }
                            }

                        } else {
                            if (isRunning) {

                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                    mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                        .sendToTarget()
                                    port1_SendSync.set()
                                }
                            }
                        }

                    } catch (e: Exception) {
                    }

                }
            }
        }
    }

    fun port2_SendData(data: ByteArray?) {
        syncFlag2 = 1
        port2_SendThread = Thread {
            while (isRunning) {
                try {
                    port2_SendSync.waitOne(500)
                    usbSerialPort_2?.write(data, WAIT_MILLIS)
                    Log.d(CHK, "port2_SendData = ${data?.toHex()}")
                    val msg: Message = mHandler.obtainMessage().apply {
                        what = SENDED_DATA
                        arg1 = 2
                        arg2 = 1
                    }
                    mHandler.sendMessage(msg)
                } catch (e: InterruptedException) {
                    val msg: Message = mHandler.obtainMessage().apply {
                        what = SENDED_DATA
                        arg1 = 2
                        arg2 = 2
                    }
                    mHandler.sendMessage(msg)
                }
            }
        }
    }

    fun port1ReceivedThread() {
        if (usbSerialPort_1 == null) return
        val parser = SerialProtocol(mDataSize!!)
        val dataSize = mDataSize!!
        val buffRecvProtocol = ByteArray(1024)
        var idxRecv = 0
        var startRecv: Long = 0
        val buff = ByteArray(1024)
        port1_ReceivedThread = Thread {
            if (syncFlag2 == 1) {
                syncFlag2 = 0
                while (isRunning) {
                    try {
                        val cnt = usbSerialPort_1!!.read(buff, WAIT_MILLIS)
                        //수신 데이터크기가 같거나 buff 내부에 존재 시
                        if (cnt > 0) {
                            //1. 버퍼 프로토콜로 데이터 이동
                            buff.copyInto(buffRecvProtocol, idxRecv, 0, cnt)

                            // 2. 버퍼 프로토콜 인덱스 변경
                            if (idxRecv == 0)
                                startRecv = System.currentTimeMillis()
//                    Log.d(TAG, "RECV DATA2 ====== ${buff.take(cnt).toByteArray().toHex()}")
                            idxRecv += cnt

                            while (true) {
                                var chkpos = indexOfBytes(buffRecvProtocol, 0, idxRecv)
                                if (chkpos != -1) {
                                    //헤더 유무 체크 및 헤더 몇번째있는지 반환
//                            Log.d(TAG, "$chkpos")
                                    var scndpos =
                                        indexOfBytes(buffRecvProtocol, chkpos + 1, idxRecv - chkpos)
                                    // 다음 데이터가 없을 경우 -1 반환 (헤더 중복체크)
                                    if (scndpos == -1) {
                                        if (idxRecv - chkpos == dataSize) {
                                            Log.d(CHK, "re1 dataSize: $dataSize")
                                            val recvdata = ByteArray(idxRecv - chkpos)
                                            buffRecvProtocol.copyInto(recvdata, 0, chkpos, idxRecv)
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                    .sendToTarget()
                                            } else {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 3)
                                                    .sendToTarget()
                                            }
                                            idxRecv = 0
                                            port1_SendSync.set()
                                        } else if (idxRecv - chkpos > dataSize) {
                                            val recvdata = ByteArray(dataSize)
                                            buffRecvProtocol.copyInto(
                                                recvdata,
                                                0,
                                                chkpos,
                                                chkpos + dataSize
                                            )
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                    .sendToTarget()
                                            }

                                            idxRecv = 0
                                            // 쓰레기 데이터 에러 추가
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                .sendToTarget()

                                            port1_SendSync.set()
                                        } else {
                                            //타임아웃
                                            if (isRunning) {
                                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                                        .sendToTarget()
                                                }
                                            }
                                            break
                                        }
                                    } else {
                                        // 중복 데이터가 있는 경우
                                        val recvdata = ByteArray(scndpos - chkpos)
                                        buffRecvProtocol.copyInto(recvdata, 0, chkpos, scndpos)
                                        if (!parser.parse(recvdata)) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                .sendToTarget()
                                        } else {
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 3)
                                                .sendToTarget()
                                        }

                                        // 1-3-2. 수신 프로토콜 데이터 당기기고 인덱스 수정
                                        val tmp = idxRecv - scndpos
//                                Log.d(TAG, "idxRecv : $idxRecv dataSize : $dataSize")
                                        val tempbuff = ByteArray(tmp)
                                        buffRecvProtocol.copyInto(
                                            tempbuff,
                                            0,
                                            scndpos,
                                            scndpos + tmp
                                        )
                                        tempbuff.copyInto(buffRecvProtocol, 0, 0, endIndex = tmp)
                                        idxRecv = tmp
//                                Log.d(TAG, "port2ReceivedThread-set2")
                                        port1_SendSync.set()
                                    }

                                } else {
                                    //타입아웃
                                    if (isRunning) {
                                        if (startRecv + time_out!! < System.currentTimeMillis()) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                                .sendToTarget()
                                        }
                                    }
                                    break
                                }
                            }

                        } else {
                            if (isRunning) {
                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                    mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                        .sendToTarget()
                                    port2_SendSync.set()
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }

                }
            }
        }
    }

    fun ByteArray.toHex(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun indexOfBytes(data: ByteArray, startIdx: Int, count: Int): Int {
        if (data.size == 0 || count == 0 || startIdx >= count)
            return -1
        var i = startIdx
        val endIndex = Math.min(startIdx + count, data.size)
        var fidx: Int = 0
        var lastFidx = 0
        while (i < endIndex) {
            lastFidx = fidx
            fidx = if (data[i] == PREFIX[fidx]) fidx + 1 else 0
            if (fidx == 2) {
                return i - fidx + 1
            }
            if (lastFidx > 0 && fidx == 0) {
                i = i - lastFidx
                lastFidx = 0
            }
            i++
        }
        return -1
    }

    fun cancelSend() {
        isRunning = false
        Thread.sleep(100)

    }

    fun singleTest(data: ByteArray?, mode: Int) {
        when (mode) {
            1 -> {
                port1_SendThread = Thread {
                    while (isRunning) {
                        try {
                            usbSerialPort_1?.write(data, WAIT_MILLIS)
                            val msg: Message = mHandler.obtainMessage().apply {
                                what = SENDED_DATA
                                arg1 = 1
                                arg2 = 1
                            }
                            mHandler.sendMessage(msg)
                            port1_SendSync.waitOne(500)
                        } catch (e: InterruptedException) {
                            val msg: Message = mHandler.obtainMessage().apply {
                                what = SENDED_DATA
                                arg1 = 1
                                arg2 = 2
                            }
                            mHandler.sendMessage(msg)

                        }
                    }
                }

                if (usbSerialPort_2 == null) return
                val parser = SerialProtocol(mDataSize!!)
                val dataSize = mDataSize!!
                val buffRecvProtocol = ByteArray(1024)
                var idxRecv = 0
                var startRecv: Long = 0
                val buff = ByteArray(1024)
                port2_ReceivedThread = Thread {
                    while (isRunning) {
                        try {
                            Log.d(TAG, "port2ReceivedThread-start")
                            val cnt = usbSerialPort_2!!.read(buff, WAIT_MILLIS)
                            //수신 데이터크기가 같거나 buff 내부에 존재 시
                            if (cnt > 0) {
                                //1. 버퍼 프로토콜로 데이터 이동
                                buff.copyInto(buffRecvProtocol, idxRecv, 0, cnt)

                                // 2. 버퍼 프로토콜 인덱스 변경
                                if (idxRecv == 0)
                                    startRecv = System.currentTimeMillis()
                                Log.d(
                                    TAG,
                                    "RECV DATA2 ====== ${buff.take(cnt).toByteArray().toHex()}"
                                )
                                idxRecv += cnt

                                while (true) {
                                    val chkpos = indexOfBytes(buffRecvProtocol, 0, idxRecv)
                                    if (chkpos != -1) {
                                        //헤더 유무 체크 및 헤더 몇번째있는지 반환
                                        Log.d(TAG, "$chkpos")
                                        val scndpos =
                                            indexOfBytes(
                                                buffRecvProtocol,
                                                chkpos + 1,
                                                idxRecv - chkpos
                                            )
                                        // 다음 데이터가 없을 경우 -1 반환 (헤더 중복체크)
                                        if (scndpos == -1) {
                                            if (idxRecv - chkpos == dataSize) {
                                                val recvdata = ByteArray(idxRecv - chkpos)
                                                buffRecvProtocol.copyInto(
                                                    recvdata,
                                                    0,
                                                    chkpos,
                                                    idxRecv
                                                )
                                                if (!parser.parse(recvdata)) {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                        .sendToTarget()
                                                } else {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 2, 3)
                                                        .sendToTarget()
                                                }
                                                idxRecv = 0
                                                port1_SendSync.set()
                                            } else {
                                                //타임아웃
                                                if (isRunning) {
                                                    if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                        mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                                            .sendToTarget()
                                                    }
                                                }
                                                break
                                            }
                                        } else {
                                            // 중복 데이터가 있는 경우
                                            val recvdata = ByteArray(scndpos - chkpos)
                                            buffRecvProtocol.copyInto(recvdata, 0, chkpos, scndpos)
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                    .sendToTarget()
                                            } else {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 3)
                                                    .sendToTarget()
                                            }

                                            // 1-3-2. 수신 프로토콜 데이터 당기기고 인덱스 수정
                                            val tmp = idxRecv - scndpos
                                            Log.d(TAG, "idxRecv : $idxRecv dataSize : $dataSize")
                                            val tempbuff = ByteArray(tmp)
                                            buffRecvProtocol.copyInto(
                                                tempbuff,
                                                0,
                                                scndpos,
                                                scndpos + tmp
                                            )
                                            tempbuff.copyInto(
                                                buffRecvProtocol,
                                                0,
                                                0,
                                                endIndex = tmp
                                            )
                                            idxRecv = tmp
                                            Log.d(TAG, "port2ReceivedThread-set2")
                                            port1_SendSync.set()
                                        }

                                    } else {
                                        //타입아웃
                                        if (isRunning) {
                                            if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                                    .sendToTarget()
                                            }
                                        }
                                        break
                                    }
                                }

                            } else {
                                if (isRunning) {
                                    if (startRecv + time_out!! < System.currentTimeMillis()) {
                                        mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                            .sendToTarget()
                                        port1_SendSync.set()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }

                    }
                }
            }
            2 -> {
                Log.d(TAG, "port2_SendData = ${data?.toHex()}")
                port2_SendThread = Thread {
                    while (isRunning) {
                        try {
                            usbSerialPort_2?.write(data, WAIT_MILLIS)
                            Log.d(TAG, "port2_SendData = ${data?.toHex()}")
                            val msg: Message = mHandler.obtainMessage().apply {
                                what = SENDED_DATA
                                arg1 = 2
                                arg2 = 1
                            }
                            mHandler.sendMessage(msg)
                            port2_SendSync.waitOne(500)
                        } catch (e: InterruptedException) {
                            val msg: Message = mHandler.obtainMessage().apply {
                                what = SENDED_DATA
                                arg1 = 2
                                arg2 = 2
                            }
                            mHandler.sendMessage(msg)

                        }
                    }
                }

                if (usbSerialPort_1 == null) return
                val parser = SerialProtocol(mDataSize!!)
                val dataSize = mDataSize!!
                val buffRecvProtocol = ByteArray(1024)
                var idxRecv = 0
                var startRecv: Long = 0
                val buff = ByteArray(1024)
                port1_ReceivedThread = Thread {
                    while (isRunning) {
                        try {
                            Log.d(TAG, "port2ReceivedThread-start")
                            val cnt = usbSerialPort_1!!.read(buff, WAIT_MILLIS)
                            //수신 데이터크기가 같거나 buff 내부에 존재 시
                            if (cnt > 0) {
                                //1. 버퍼 프로토콜로 데이터 이동
                                buff.copyInto(buffRecvProtocol, idxRecv, 0, cnt)

                                // 2. 버퍼 프로토콜 인덱스 변경
                                if (idxRecv == 0)
                                    startRecv = System.currentTimeMillis()
                                Log.d(
                                    TAG,
                                    "RECV DATA2 ====== ${buff.take(cnt).toByteArray().toHex()}"
                                )
                                idxRecv += cnt

                                while (true) {
                                    val chkpos = indexOfBytes(buffRecvProtocol, 0, idxRecv)
                                    if (chkpos != -1) {
                                        //헤더 유무 체크 및 헤더 몇번째있는지 반환
                                        Log.d(TAG, "$chkpos")
                                        val scndpos =
                                            indexOfBytes(
                                                buffRecvProtocol,
                                                chkpos + 1,
                                                idxRecv - chkpos
                                            )
                                        // 다음 데이터가 없을 경우 -1 반환 (헤더 중복체크)
                                        if (scndpos == -1) {
                                            if (idxRecv - chkpos == dataSize) {
                                                val recvdata = ByteArray(idxRecv - chkpos)
                                                buffRecvProtocol.copyInto(
                                                    recvdata,
                                                    0,
                                                    chkpos,
                                                    idxRecv
                                                )
                                                if (!parser.parse(recvdata)) {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                        .sendToTarget()
                                                } else {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 1, 3)
                                                        .sendToTarget()
                                                }
                                                idxRecv = 0
                                                port2_SendSync.set()
                                            } else {
                                                //타임아웃
                                                if (isRunning) {
                                                    if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                        mHandler.obtainMessage(
                                                            RECEIVED_DATA,
                                                            1,
                                                            2
                                                        )
                                                            .sendToTarget()
                                                    }
                                                }
                                                break
                                            }
                                        } else {
                                            // 중복 데이터가 있는 경우
                                            val recvdata = ByteArray(scndpos - chkpos)
                                            buffRecvProtocol.copyInto(recvdata, 0, chkpos, scndpos)
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                    .sendToTarget()
                                            } else {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 3)
                                                    .sendToTarget()
                                            }

                                            // 1-3-2. 수신 프로토콜 데이터 당기기고 인덱스 수정
                                            val tmp = idxRecv - scndpos
                                            Log.d(TAG, "idxRecv : $idxRecv dataSize : $dataSize")
                                            val tempbuff = ByteArray(tmp)
                                            buffRecvProtocol.copyInto(
                                                tempbuff,
                                                0,
                                                scndpos,
                                                scndpos + tmp
                                            )
                                            tempbuff.copyInto(
                                                buffRecvProtocol,
                                                0,
                                                0,
                                                endIndex = tmp
                                            )
                                            idxRecv = tmp
                                            Log.d(TAG, "port2ReceivedThread-set2")
                                            port2_SendSync.set()
                                        }
                                    } else {
                                        //타입아웃
                                        if (isRunning) {
                                            if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                                    .sendToTarget()
                                            }
                                        }
                                        break
                                    }
                                }
                            } else {
                                if (isRunning) {
                                    if (startRecv + time_out!! < System.currentTimeMillis()) {
                                        mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                            .sendToTarget()
                                        port2_SendSync.set()
                                    }
                                }
                            }

                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
    }

    var syncFlag1 = 0
    var syncFlag2 = 0

    //send isrunning 확인하기
    fun port1_AutoSendData(data: ByteArray?, buadrate: Int) {
        syncFlag1 = 1
        port1_SendThread = Thread {
            while (isRunning) {
                for (i in 1..100) {
                    try {
                        usbSerialPort_1?.write(data, WAIT_MILLIS)
                        Log.d(CHK, "port1_SendData = ${data?.toHex()}")
                        val msg: Message = mHandler.obtainMessage().apply {
                            what = SENDED_DATA
                            arg1 = 1
                            arg2 = 1
                        }
                        mHandler.sendMessage(msg)
                        port1_SendSync.waitOne(500)
                    } catch (e: InterruptedException) {
                        val msg: Message = mHandler.obtainMessage().apply {
                            what = SENDED_DATA
                            arg1 = 1
                            arg2 = 2
                        }
                        mHandler.sendMessage(msg)

                    }
                }
                break
            }
        }
    }

    fun port2_AutoReceivedThread() {
        if (usbSerialPort_2 == null) return
        val parser = SerialProtocol(mDataSize!!)
        val dataSize = mDataSize!!
        val buffRecvProtocol = ByteArray(1024)
        var idxRecv = 0
        var startRecv: Long = System.currentTimeMillis()
        val buff = ByteArray(1024)
        port2_ReceivedThread = Thread {
            if (syncFlag1 == 1) {
                syncFlag1 = 0
                while (isRunning) {
                    try {
                        val cnt = usbSerialPort_2!!.read(buff, WAIT_MILLIS)
                        //수신 데이터크기가 같거나 buff 내부에 존재 시
                        if (cnt > 0) {
                            //1. 버퍼 프로토콜로 데이터 이동
                            buff.copyInto(buffRecvProtocol, idxRecv, 0, cnt)

                            // 2. 버퍼 프로토콜 인덱스 변경
                            if (idxRecv == 0)
                                startRecv = System.currentTimeMillis()
                            //                    Log.d(TAG, "RECV DATA2 ====== ${buff.take(cnt).toByteArray().toHex()}")
                            idxRecv += cnt
                            while (true) {
                                val chkpos = indexOfBytes(buffRecvProtocol, 0, idxRecv)
                                if (chkpos != -1) {
                                    //헤더 유무 체크 및 헤더 몇번째있는지 반환
                                    //                            Log.d(TAG, "$chkpos")
                                    val scndpos =
                                        indexOfBytes(buffRecvProtocol, chkpos + 1, idxRecv - chkpos)
                                    // 다음 데이터가 없을 경우 -1 반환 (헤더 중복체크)
                                    if (scndpos == -1) {
                                        if (idxRecv - chkpos == dataSize) {
                                            Log.d(CHK, "re2 dataSize: $dataSize")
                                            val recvdata = ByteArray(dataSize)
                                            buffRecvProtocol.copyInto(recvdata, 0, chkpos, idxRecv)
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                    .sendToTarget()
                                            } else {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 3)
                                                    .sendToTarget()
                                            }
                                            idxRecv = 0
                                            port2_SendSync.set()
                                        } else if (idxRecv - chkpos > dataSize) {
                                            Log.d(CHK, "re2 dataSize: $dataSize")
                                            val recvdata = ByteArray(dataSize)
                                            buffRecvProtocol.copyInto(
                                                recvdata,
                                                0,
                                                chkpos,
                                                chkpos + dataSize
                                            )
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                    .sendToTarget()
                                            }

                                            idxRecv = 0
                                            // 쓰레기 데이터 에러 추가
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                .sendToTarget()

                                            port2_SendSync.set()
                                        } else {

                                            //타임아웃
                                            if (isRunning) {

                                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                                        .sendToTarget()
                                                }
                                            }
                                            break
                                        }
                                    } else {
                                        // 중복 데이터가 있는 경우
                                        val recvdata = ByteArray(scndpos - chkpos)
                                        buffRecvProtocol.copyInto(recvdata, 0, chkpos, scndpos)
                                        if (!parser.parse(recvdata)) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 1)
                                                .sendToTarget()
                                        } else {
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 3)
                                                .sendToTarget()
                                        }

                                        // 1-3-2. 수신 프로토콜 데이터 당기기고 인덱스 수정
                                        val tmp = idxRecv - scndpos
                                        //                                Log.d(TAG, "idxRecv : $idxRecv dataSize : $dataSize")
                                        val tempbuff = ByteArray(tmp)
                                        buffRecvProtocol.copyInto(
                                            tempbuff,
                                            0,
                                            scndpos,
                                            scndpos + tmp
                                        )
                                        tempbuff.copyInto(buffRecvProtocol, 0, 0, endIndex = tmp)
                                        idxRecv = tmp
                                        //                                Log.d(TAG, "port2ReceivedThread-set2")
                                        port2_SendSync.set()
                                    }

                                } else {
                                    //타입아웃
                                    if (isRunning) {

                                        if (startRecv + time_out!! < System.currentTimeMillis()) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                                .sendToTarget()
                                        }
                                    }
                                    break
                                }
                            }

                        } else {
                            if (isRunning) {

                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                    mHandler.obtainMessage(RECEIVED_DATA, 2, 2)
                                        .sendToTarget()
                                    port1_SendSync.set()
                                }
                            }
                        }

                    } catch (e: Exception) {
                    }

                }

            }
        }
    }

    fun port2_AutoSendData(data: ByteArray?, buadrate: Int) {
        syncFlag2 = 1
        port2_SendThread = Thread {
            while (isRunning) {
                for (i in 1..100) {
                    try {
                        port2_SendSync.waitOne(500)
                        usbSerialPort_2?.write(data, WAIT_MILLIS)
                        Log.d(CHK, "port2_SendData = ${data?.toHex()}")
                        val msg: Message = mHandler.obtainMessage().apply {
                            what = SENDED_DATA
                            arg1 = 2
                            arg2 = 1
                        }
                        mHandler.sendMessage(msg)
                    } catch (e: InterruptedException) {
                        val msg: Message = mHandler.obtainMessage().apply {
                            what = SENDED_DATA
                            arg1 = 2
                            arg2 = 2
                        }
                        mHandler.sendMessage(msg)
                    }
                }
                break
            }
            if (buadrate == MainViewModel.LIST_OF_BAUD_RATE[6]) {
                val msg: Message = mHandler.obtainMessage().apply {
                    what = 5
                    obj = 1
                }
                mHandler.sendMessage(msg)

            } else if (buadrate == MainViewModel.LIST_OF_BAUD_RATE[0]) {
                val msg: Message = mHandler.obtainMessage().apply {
                    what = 5
                    obj = 3
                }
                mHandler.sendMessage(msg)
            } else {
                val msg: Message = mHandler.obtainMessage().apply {
                    what = 5
                    obj = 2
                }
                mHandler.sendMessage(msg)
            }


        }
    }

    fun port1_AutoReceivedThread() {
        if (usbSerialPort_1 == null) return
        val parser = SerialProtocol(mDataSize!!)
        val dataSize = mDataSize!!
        val buffRecvProtocol = ByteArray(1024)
        var idxRecv = 0
        var startRecv: Long = 0
        val buff = ByteArray(1024)
        port1_ReceivedThread = Thread {
            if (syncFlag2 == 1) {
                syncFlag2 = 0
                while (isRunning) {
                    try {
                        val cnt = usbSerialPort_1!!.read(buff, WAIT_MILLIS)
                        //수신 데이터크기가 같거나 buff 내부에 존재 시
                        if (cnt > 0) {
                            //1. 버퍼 프로토콜로 데이터 이동
                            buff.copyInto(buffRecvProtocol, idxRecv, 0, cnt)

                            // 2. 버퍼 프로토콜 인덱스 변경
                            if (idxRecv == 0)
                                startRecv = System.currentTimeMillis()
//                    Log.d(TAG, "RECV DATA2 ====== ${buff.take(cnt).toByteArray().toHex()}")
                            idxRecv += cnt

                            while (true) {
                                val chkpos = indexOfBytes(buffRecvProtocol, 0, idxRecv)
                                if (chkpos != -1) {
                                    //헤더 유무 체크 및 헤더 몇번째있는지 반환
//                            Log.d(TAG, "$chkpos")
                                    val scndpos =
                                        indexOfBytes(buffRecvProtocol, chkpos + 1, idxRecv - chkpos)
                                    // 다음 데이터가 없을 경우 -1 반환 (헤더 중복체크)
                                    if (scndpos == -1) {
                                        if (idxRecv - chkpos == dataSize) {
                                            Log.d(CHK, "re1 dataSize: $dataSize")
                                            val recvdata = ByteArray(idxRecv - chkpos)
                                            buffRecvProtocol.copyInto(recvdata, 0, chkpos, idxRecv)
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                    .sendToTarget()
                                            } else {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 3)
                                                    .sendToTarget()
                                            }
                                            idxRecv = 0
                                            port1_SendSync.set()
                                        } else if (idxRecv - chkpos > dataSize) {
                                            val recvdata = ByteArray(dataSize)
                                            buffRecvProtocol.copyInto(
                                                recvdata,
                                                0,
                                                chkpos,
                                                chkpos + dataSize
                                            )
                                            if (!parser.parse(recvdata)) {
                                                mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                    .sendToTarget()
                                            }

                                            idxRecv = 0
                                            // 쓰레기 데이터 에러 추가
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                .sendToTarget()

                                            port1_SendSync.set()
                                        } else {
                                            //타임아웃
                                            if (isRunning) {
                                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                                    mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                                        .sendToTarget()
                                                }
                                            }
                                            break
                                        }
                                    } else {
                                        // 중복 데이터가 있는 경우
                                        val recvdata = ByteArray(scndpos - chkpos)
                                        buffRecvProtocol.copyInto(recvdata, 0, chkpos, scndpos)
                                        if (!parser.parse(recvdata)) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 1)
                                                .sendToTarget()
                                        } else {
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 3)
                                                .sendToTarget()
                                        }

                                        // 1-3-2. 수신 프로토콜 데이터 당기기고 인덱스 수정
                                        val tmp = idxRecv - scndpos
//                                Log.d(TAG, "idxRecv : $idxRecv dataSize : $dataSize")
                                        val tempbuff = ByteArray(tmp)
                                        buffRecvProtocol.copyInto(
                                            tempbuff,
                                            0,
                                            scndpos,
                                            scndpos + tmp
                                        )
                                        tempbuff.copyInto(buffRecvProtocol, 0, 0, endIndex = tmp)
                                        idxRecv = tmp
//                                Log.d(TAG, "port2ReceivedThread-set2")
                                        port1_SendSync.set()
                                    }

                                } else {
                                    //타입아웃
                                    if (isRunning) {
                                        if (startRecv + time_out!! < System.currentTimeMillis()) {
                                            mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                                .sendToTarget()
                                        }
                                    }
                                    break
                                }
                            }

                        } else {
                            if (isRunning) {
                                if (startRecv + time_out!! < System.currentTimeMillis()) {
                                    mHandler.obtainMessage(RECEIVED_DATA, 1, 2)
                                        .sendToTarget()
                                    port2_SendSync.set()
                                }
                            }
                        }

                    } catch (e: Exception) {
                    }
                }

            }

        }
    }

    class RXTXSynchronized {
        val TAG = "로그"
        private val _monitor = Object()
        private var _isOpen = false
        private var result = null
        private var isWaiting = false
        private var wasNotified = false


        fun SynchroniedService(open: Boolean) {
            _isOpen = open
        }

        fun waitOne() {
            synchronized(_monitor) {
                while (!_isOpen) {
                    _monitor.wait()
                }
                _isOpen = false
            }
        }

        @Throws(InterruptedException::class)
        fun waitOne(timeout: Long) {
            synchronized(_monitor) {
                try {
                    val t = System.currentTimeMillis()
                    while (!_isOpen) {
                        _monitor.wait(timeout)
                        // Check for timeout
//                    Log.d(TAG, "waitOne $timeout")
                        if (System.currentTimeMillis() - t >= timeout) {
                            throw InterruptedException("assssssss!!!")
                        }
                    }
                    _isOpen = false

                } catch (e: Exception) {
                }
            }
        }

        fun set() {
            synchronized(_monitor) {
                _isOpen = true
                _monitor.notify()
            }
        }

        fun reset() {
            _isOpen = false
        }

        fun setResultAndNotify() {
            if (isWaiting) {
                synchronized(_monitor) {
                    _monitor.notify()
                }
            } else {
                wasNotified = true
            }
        }

        fun waitAndGetResult(timeout: Long): Nothing? {
            if (wasNotified) {
                wasNotified = false
                return result
            }
            try {
                synchronized(_monitor) {
                    isWaiting = true
                    if (timeout < 0) _monitor.wait()
                    else _monitor.wait(timeout)
                    isWaiting = false
                }
            } catch (e: InterruptedException) {
                Log.getStackTraceString(e)
            }
            return this.result

            fun waitAndGetResult() = waitAndGetResult(-1)
        }

        fun masterThreadWork() {
            Log.d(TAG, "port1ThreadStart-masterThreadWork()")

            synchronized(_monitor) {
                Log.d(TAG, "port1ThreadStart-notify()")
                _monitor.notify()

                try {
                    Log.d(TAG, "port1ThreadStart-wait()")
                    _monitor.wait()
                } catch (e: Exception) {
                }
            }
        }

        fun receivedWork() {
            synchronized(_monitor) {
                Log.d(TAG, "port1ThreadStart-notify()")
                _monitor.notify()

            }
        }
    }
}

