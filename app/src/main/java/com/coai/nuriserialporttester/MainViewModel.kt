package com.coai.nuriserialporttester

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    companion object {
        val LIST_OF_BAUD_RATE = arrayOf(
            9600,
            19200,
            57600,
            115200,
            250000,
            500000,
            1000000
        )

    }

    //내부에서 설정하는 자료형은 뮤터블로
    //변경가능하도록 설정
    private val _IDsList = MutableLiveData<List<Byte>>()
    private val _D_size = MutableLiveData<Int>()
    private val _parser = MutableLiveData<SerialProtocol>()

    // 변경되지 않는 데이터를 가져올때 이름을 _ 언더스코어 없이 설정
    // 공개적으로 가져오는 변수는 private 이 아닌 퍼블릭으로 외부에서도 접근가능하도록 설정
    // 하지만 값을 직접 라이브데이터에 접근하지 않고 뷰모델을 통해 가져올수 있도록 설정
    val IDsList: MutableLiveData<List<Byte>>
        get() = _IDsList

    val D_size: MutableLiveData<Int>
        get() = _D_size

    val parser: MutableLiveData<SerialProtocol>
        get() = _parser
    init {
//        Log.d(TAG, "MainViewModel - 생성자 호출")
//        Log.d(TAG, "MainViewModel _currentValue : $_currentValue")
//        _IDsList.value = IDs

    }
}