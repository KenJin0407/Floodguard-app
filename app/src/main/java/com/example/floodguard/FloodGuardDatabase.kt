package com.example.floodguard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * Local SQLite OpenHelper database manager for FloodGuard.
 * Stores local user profiles, offline credentials, and community flood incident reports.
 */
class FloodGuardDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "floodguard.db"
        private const val DATABASE_VERSION = 7

        @Volatile
        private var instance: FloodGuardDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): FloodGuardDatabase {
            return instance ?: synchronized(this) {
                instance ?: FloodGuardDatabase(context.applicationContext).also { instance = it }
            }
        }

        const val TABLE_USERS = "users"
        const val COLUMN_USER_ID = "id"
        const val COLUMN_FULL_NAME = "full_name"
        const val COLUMN_GENDER = "gender"
        const val COLUMN_MOBILE = "mobile"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_PASSWORD = "password"

        const val TABLE_REPORTS = "reports"
        const val COLUMN_REPORT_ID = "id"
        const val COLUMN_LOCATION = "location"
        const val COLUMN_SEVERITY = "severity"
        const val COLUMN_STATUS_TEXT = "status_text"
        const val COLUMN_NOTES = "notes"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_SEVERITY_LEVEL = "severity_level"
        const val COLUMN_PHOTO_URI = "photo_uri"
        const val COLUMN_LAT = "lat"
        const val COLUMN_LON = "lon"
        const val COLUMN_AUTHOR_USERNAME = "author_username"
        const val COLUMN_REPORTER_NAME = "reporter_name"
        const val COLUMN_REPORTER_USERNAME = "reporter_username"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_USER_ID TEXT PRIMARY KEY,
                $COLUMN_FULL_NAME TEXT,
                $COLUMN_GENDER TEXT,
                $COLUMN_MOBILE TEXT,
                $COLUMN_USERNAME TEXT UNIQUE,
                $COLUMN_PASSWORD TEXT
            )
        """.trimIndent()

        val createReportsTable = """
            CREATE TABLE $TABLE_REPORTS (
                $COLUMN_REPORT_ID TEXT PRIMARY KEY,
                $COLUMN_LOCATION TEXT,
                $COLUMN_SEVERITY TEXT,
                $COLUMN_STATUS_TEXT TEXT,
                $COLUMN_NOTES TEXT,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_SEVERITY_LEVEL TEXT,
                $COLUMN_PHOTO_URI TEXT,
                $COLUMN_LAT REAL,
                $COLUMN_LON REAL,
                $COLUMN_AUTHOR_USERNAME TEXT DEFAULT '',
                $COLUMN_REPORTER_NAME TEXT DEFAULT ''
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createReportsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_REPORTS")
            onCreate(db)
            return
        }
        ensureReportColumns(db)
        removeEmailColumnFromUsersTable(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureReportColumns(db)
        removeEmailColumnFromUsersTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            ensureReportColumns(db)
            removeEmailColumnFromUsersTable(db)
        }
    }

    private fun removeEmailColumnFromUsersTable(db: SQLiteDatabase) {
        val userColumns = mutableSetOf<String>()
        try {
            val cursor = db.rawQuery("PRAGMA table_info($TABLE_USERS)", null)
            val nameIdx = cursor.getColumnIndex("name")
            if (cursor.moveToFirst() && nameIdx >= 0) {
                do {
                    userColumns.add(cursor.getString(nameIdx))
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (_: Exception) {
        }

        if (userColumns.contains("email")) {
            try {
                db.beginTransaction()
                db.execSQL(
                    """
                    CREATE TABLE ${TABLE_USERS}_new (
                        $COLUMN_USER_ID TEXT PRIMARY KEY,
                        $COLUMN_FULL_NAME TEXT,
                        $COLUMN_GENDER TEXT,
                        $COLUMN_MOBILE TEXT,
                        $COLUMN_USERNAME TEXT UNIQUE,
                        $COLUMN_PASSWORD TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO ${TABLE_USERS}_new (
                        $COLUMN_USER_ID, $COLUMN_FULL_NAME, $COLUMN_GENDER, $COLUMN_MOBILE, $COLUMN_USERNAME, $COLUMN_PASSWORD
                    )
                    SELECT $COLUMN_USER_ID, $COLUMN_FULL_NAME, $COLUMN_GENDER, $COLUMN_MOBILE, $COLUMN_USERNAME, $COLUMN_PASSWORD
                    FROM $TABLE_USERS
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE $TABLE_USERS")
                db.execSQL("ALTER TABLE ${TABLE_USERS}_new RENAME TO $TABLE_USERS")
                db.setTransactionSuccessful()
            } catch (_: Exception) {
            } finally {
                try {
                    db.endTransaction()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun ensureReportColumns(db: SQLiteDatabase) {
        val existingColumns = mutableSetOf<String>()
        try {
            val cursor = db.rawQuery("PRAGMA table_info($TABLE_REPORTS)", null)
            val nameIdx = cursor.getColumnIndex("name")
            if (cursor.moveToFirst() && nameIdx >= 0) {
                do {
                    existingColumns.add(cursor.getString(nameIdx))
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (_: Exception) {
        }

        if (existingColumns.isNotEmpty() && !existingColumns.contains(COLUMN_AUTHOR_USERNAME)) {
            try {
                db.execSQL("ALTER TABLE $TABLE_REPORTS ADD COLUMN $COLUMN_AUTHOR_USERNAME TEXT DEFAULT ''")
            } catch (_: Exception) {
            }
        }
        if (existingColumns.isNotEmpty() && !existingColumns.contains(COLUMN_REPORTER_NAME)) {
            try {
                db.execSQL("ALTER TABLE $TABLE_REPORTS ADD COLUMN $COLUMN_REPORTER_NAME TEXT DEFAULT ''")
            } catch (_: Exception) {
            }
        }
    }

    fun insertReport(report: FloodReport): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COLUMN_REPORT_ID, report.id)
            put(COLUMN_LOCATION, report.location)
            put(COLUMN_SEVERITY, report.severity)
            put(COLUMN_STATUS_TEXT, report.statusText)
            put(COLUMN_NOTES, report.notes)
            put(COLUMN_TIMESTAMP, report.timestamp)
            put(COLUMN_SEVERITY_LEVEL, report.severityLevel.name)
            put(COLUMN_PHOTO_URI, report.photoUri)
            put(COLUMN_LAT, report.lat)
            put(COLUMN_LON, report.lon)
            val author = report.authorUsername.ifBlank { report.reporterUsername }
            put(COLUMN_AUTHOR_USERNAME, author)
            put(COLUMN_REPORTER_NAME, report.reporterName)
        }
        val result = db.insertWithOnConflict(TABLE_REPORTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return result != -1L
    }

    fun getAllReports(): List<FloodReport> {
        val reportList = mutableListOf<FloodReport>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_REPORTS ORDER BY $COLUMN_TIMESTAMP DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REPORT_ID))
                val location = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCATION))
                val severity = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SEVERITY))
                val statusText = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS_TEXT))
                val notes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val severityLevelStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SEVERITY_LEVEL))
                val photoUri = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHOTO_URI))
                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LAT))
                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LON))
                val authorIdx = cursor.getColumnIndex(COLUMN_AUTHOR_USERNAME)
                val authorUsername = if (authorIdx >= 0 && !cursor.isNull(authorIdx)) cursor.getString(authorIdx) else ""
                val reporterIdx = cursor.getColumnIndex(COLUMN_REPORTER_NAME)
                val reporterName = if (reporterIdx >= 0 && !cursor.isNull(reporterIdx)) cursor.getString(reporterIdx) else ""

                val severityLevel = try {
                    SeverityLevel.valueOf(severityLevelStr)
                } catch (_: Exception) {
                    SeverityLevel.NOT_PASSABLE
                }

                reportList.add(
                    FloodReport(
                        id = id,
                        location = location,
                        severity = severity,
                        statusText = statusText,
                        notes = notes,
                        timestamp = timestamp,
                        severityLevel = severityLevel,
                        photoUri = photoUri,
                        lat = if (lat == 0.0) 14.6958 else lat,
                        lon = if (lon == 0.0) 121.1207 else lon,
                        authorUsername = authorUsername,
                        reporterName = reporterName,
                        reporterUsername = authorUsername,
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return reportList
    }

    fun updateReport(
        id: String,
        location: String,
        severity: String,
        statusText: String,
        notes: String,
        severityLevel: SeverityLevel,
        authorUsername: String? = null,
        reporterName: String? = null,
        photoUri: String? = null,
    ): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COLUMN_LOCATION, location)
            put(COLUMN_SEVERITY, severity)
            put(COLUMN_STATUS_TEXT, statusText)
            put(COLUMN_NOTES, notes)
            put(COLUMN_SEVERITY_LEVEL, severityLevel.name)
            if (!authorUsername.isNullOrBlank()) {
                put(COLUMN_AUTHOR_USERNAME, authorUsername)
            }
            if (!reporterName.isNullOrBlank()) {
                put(COLUMN_REPORTER_NAME, reporterName)
            }
            if (!photoUri.isNullOrBlank()) {
                put(COLUMN_PHOTO_URI, photoUri)
            }
        }
        val result = db.update(TABLE_REPORTS, cv, "$COLUMN_REPORT_ID=?", arrayOf(id))
        return result > 0
    }

    fun deleteReport(id: String): Boolean {
        val db = writableDatabase
        val result = db.delete(TABLE_REPORTS, "$COLUMN_REPORT_ID=?", arrayOf(id))
        return result > 0
    }

    fun registerUser(user: UserEntity): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COLUMN_USER_ID, user.id)
            put(COLUMN_FULL_NAME, user.fullName)
            put(COLUMN_GENDER, user.gender)
            put(COLUMN_MOBILE, user.mobile)
            put(COLUMN_USERNAME, user.username)
            put(COLUMN_PASSWORD, user.password)
        }
        val result = db.insertWithOnConflict(TABLE_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return result != -1L
    }

    fun getUserByUsername(username: String): UserEntity? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COLUMN_USERNAME = ? COLLATE NOCASE",
            arrayOf(username.trim())
        )
        var user: UserEntity? = null
        if (cursor.moveToFirst()) {
            user = UserEntity(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)),
                fullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME)),
                gender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GENDER)),
                mobile = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MOBILE)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
            )
        }
        cursor.close()
        return user
    }

    fun getUserByFullName(fullName: String): UserEntity? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COLUMN_FULL_NAME = ? COLLATE NOCASE",
            arrayOf(fullName.trim())
        )
        var user: UserEntity? = null
        if (cursor.moveToFirst()) {
            user = UserEntity(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)),
                fullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME)),
                gender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GENDER)),
                mobile = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MOBILE)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
            )
        }
        cursor.close()
        return user
    }

    fun updateUserProfile(username: String, fullName: String, gender: String, mobile: String): Boolean {
        val existing = getUserByUsername(username)
        val db = writableDatabase
        return if (existing != null) {
            val cv = ContentValues().apply {
                put(COLUMN_FULL_NAME, fullName.trim())
                put(COLUMN_GENDER, gender)
                put(COLUMN_MOBILE, mobile.trim())
            }
            db.update(TABLE_USERS, cv, "$COLUMN_USERNAME = ? COLLATE NOCASE", arrayOf(username.trim())) > 0
        } else {
            registerUser(
                UserEntity(
                    fullName = fullName.trim(),
                    gender = gender,
                    mobile = mobile.trim(),
                    username = username.trim(),
                    password = "password123",
                )
            )
        }
    }

    fun updatePassword(username: String, newPasswordHash: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COLUMN_PASSWORD, newPasswordHash)
        }
        val rows = db.update(
            TABLE_USERS,
            cv,
            "$COLUMN_USERNAME = ? COLLATE NOCASE",
            arrayOf(username.trim())
        )
        return rows > 0
    }

    fun getAllUsers(): List<UserEntity> {
        val list = mutableListOf<UserEntity>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USERS", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(
                    UserEntity(
                        id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)),
                        fullName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FULL_NAME)),
                        gender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GENDER)),
                        mobile = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MOBILE)),
                        username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME)),
                        password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getAllUserMobiles(): List<String> {
        val list = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_MOBILE FROM $TABLE_USERS WHERE $COLUMN_MOBILE IS NOT NULL AND $COLUMN_MOBILE != ''", null)
        if (cursor.moveToFirst()) {
            do {
                val mobile = cursor.getString(0)
                if (!mobile.isNullOrBlank()) {
                    list.add(mobile.trim())
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
}

data class UserEntity(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val gender: String,
    val mobile: String,
    val username: String,
    val password: String,
)
