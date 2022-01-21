package com.coai.nuriserialporttester

class SerialProtocol {
    var size: Int = 20
    val mData: ByteArray
    //    var gData: ByteArray = ByteArray(size - 7)
    val stx = byteArrayOf(0xff.toByte(), 0xfe.toByte())

    constructor(size: Int){
        this.size = size
        this.mData = ByteArray(this.size)

    }

    fun BuildProtocol(cid: Byte, mode: Byte, data: Byte? = null): ByteArray {
        var ret: ByteArray
        if (data == null) {
            ret = ByteArray(size)
            ret[3] = 2
        } else {

            ret = ByteArray(size)
            ret[3] = 3
            ret[6] = data
//            for (i in 7..99){e
//
//            }
        }
        ret[0] = 0xff.toByte()
        ret[1] = 0xfe.toByte()
        ret[2] = cid
        ret[5] = mode

        ret[4] = checkSum(cid, mode, data)
        return ret
    }

    fun BuildProtocoltest(dataSize: Int): ByteArray {
        val ret = ByteArray(dataSize)
        ret[0] = 0xff.toByte()
        ret[1] = 0xfe.toByte()
        for (i in 0..dataSize - 3) {
            ret.set(2 + i, i.toByte())
        }
        return ret
    }

    fun getDataSize(): Int {
        return mData.size
    }

    fun BuzzerOn(cid: Byte): ByteArray? {
        return BuildProtocol(cid, 0xa2.toByte(), 0x01)
    }

    fun checkSum(cid: Byte, mode: Byte, data: Byte? = null): Byte {
        if (data == null)
            return ((cid + 2 + mode) % 256).toByte()
        else
            return ((cid + 3 + mode + data) % 256).toByte()

    }

    fun checkSumRecive(data: ByteArray): Byte {
        val sumvalue = (data.sum() - 0xff - 0xfe - data[4])

        return (sumvalue % 256).toByte()
    }


    var modeIDXs = intArrayOf(5, 8, 11, 14, 17)
    var modes = byteArrayOf(1, 2)
    fun Checkmode(data: ByteArray): Boolean {
        var ret: Boolean = true
        modeIDXs.forEach { i ->
            modeIDXs
            if (!modes.contains(data[i])) {
                ret = false
                return@forEach
            }

        }
        return ret
    }


    fun checkHeader(): Boolean {
        return (mData[0] == 0xff.toByte()
                && mData[1] == 0xfe.toByte())
    }

    fun checkDataSize(data: ByteArray): Boolean {
        return (mData.size == data.size)
    }

//    fun checkGarbageData(): Boolean {
//        val temp = mData.sliceArray(7 until size)
//        return (gData.contentEquals(temp))
//    }

    fun parse(data: ByteArray): Boolean {

        // STX 부터 짜르기
        try {
            val idx = data.findFirst(stx)
//            Log.d("index", "인덱스 : $idx")
//            val datatmp: ByteArray = data.drop(idx).take(100).toByteArray()
//            mData = data.drop(idx).take(100).toByteArray()
//            Log.d("data", Hex.encodeHexString(data))
            data.copyInto(mData, 0, idx, size)

            // 데이터 사이즈 체크
            if (checkDataSize(mData)) {
                // 파싱 데이터 적제
//                datatmp.copyInto(mData)
                // 사이즈 헤더 비교 호출
                if (checkHeader()) {
                    // 체크섬 비교 체크
                    if (mData[4] == checkSumRecive(mData)) {
                        //가비지 데이터 비교
//                        if (checkGarbageData()) {
//                            // 결과 응답
//                        }
                    }
                }
            } else {
                return false
            }
        } catch (e: Exception) {
            return false
        }

        return true
    }
}

fun ByteArray.findFirst(sequence: ByteArray, startFrom: Int = 0): Int {
    if (sequence.isEmpty()) return -1
    if (startFrom < 0) return -1
    var matchOffset = 0
    var start = startFrom
    var offset = startFrom
    while (offset < size) {
        if (this[offset] == sequence[matchOffset]) {
            if (matchOffset++ == 0) start = offset
            if (matchOffset == sequence.size) return start
        } else
            matchOffset = 0
        offset++
    }
    return -1
}
