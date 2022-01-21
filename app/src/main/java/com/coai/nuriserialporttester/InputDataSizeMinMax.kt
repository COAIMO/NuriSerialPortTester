package com.coai.nuriserialporttester

import android.text.InputFilter
import android.text.Spanned
import java.lang.NumberFormatException


class InputDataSizeMinMax : InputFilter {
    private var mMax:Int = 0
    private var mMin:Int = 0

    constructor(min:Int, max:Int){
        this.mMax = max
        this.mMin = min
    }

    constructor(min:String, max:String){
        this.mMax = Integer.parseInt(max)
        this.mMin = Integer.parseInt(min)
    }


    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        try {
            val input = (dest.toString() + source.toString()).toInt()
            if (isInRange(mMin, mMax, input)) return null
        } catch (nfe: NumberFormatException) {
        }
        return ""
    }

    private fun isInRange(a: Int, b: Int, c: Int): Boolean {
        return if (b > a) c >= a && c <= b else c >= b && c <= a
    }

}