package meimaonamassa.cashflow.base

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import meimaonamassa.cashflow.data.entity.TransactionEntity
import meimaonamassa.cashflow.data.local.TransactionDAO
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Database(entities = [TransactionEntity::class], version = 6, exportSchema = false)
@TypeConverters(DateConverters::class)
abstract class TransactionDatabase : RoomDatabase() {
    abstract fun transactionDAO() : TransactionDAO

    companion object {
        private const val DATABASE_NAME = "transaction_database"
        @Volatile
        private var instance: TransactionDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transaction_table RENAME COLUMN date TO payment_date")
                db.execSQL("""
                    ALTER TABLE transaction_table ADD COLUMN issue_date TEXT 
                        NOT NULL DEFAULT '1900-01-01T00:00:00Z'
                """.trimIndent())
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transaction_table ADD COLUMN bank_account TEXT NOT NULL DEFAULT 'Carteira'"
                )
            }
        }
        fun getDatabase(context: Context): TransactionDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also {
                    instance = it
                }
            }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context.applicationContext,
            TransactionDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()

        fun exportDatabase(context: Context, outputStream: OutputStream): Boolean {
            return try {
                val db = getDatabase(context)
                val cursor = db.openHelper.writableDatabase.query(
                    SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")
                )
                if (cursor.moveToFirst()) {
                    cursor.close()
                }

                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (!dbFile.exists()) return false

                dbFile.inputStream().use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun importDatabase(context: Context, inputStream: InputStream): Boolean {
            return try {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                val walFile = context.getDatabasePath("$DATABASE_NAME-wal")
                val shmFile = context.getDatabasePath("$DATABASE_NAME-shm")

                synchronized(this) {
                    if (instance?.isOpen == true) {
                        try {
                            instance?.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    instance = null
                }

                dbFile.parentFile?.mkdirs()

                val tempFile = File(context.cacheDir, "temp_restore.db")
                tempFile.outputStream().use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }

                if (tempFile.length() < 16) {
                    tempFile.delete()
                    return false
                }
                val header = ByteArray(16)
                tempFile.inputStream().use { it.read(header) }
                val headerString = String(header, Charsets.US_ASCII)
                if (!headerString.startsWith("SQLite format 3")) {
                    tempFile.delete()
                    return false
                }

                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                val copied = tempFile.copyTo(dbFile, overwrite = true)
                tempFile.delete()

                getDatabase(context).openHelper.writableDatabase
                copied.exists()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
