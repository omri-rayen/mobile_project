package com.studyhelper

import android.app.Application
import com.studyhelper.data.AppDatabase

class StudyApp : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }
}
