package meimaonamassa.cashflow.base

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import meimaonamassa.cashflow.data.entity.TransactionEntity
import meimaonamassa.cashflow.data.local.TransactionDAO

@Database(entities = [TransactionEntity::class], version = 4)
@TypeConverters(DateConverters::class)
abstract class TransactionDatabase : RoomDatabase() {
    abstract fun transactionDAO() : TransactionDAO

    companion object {
        private const val DATABASE_NAME = "transaction_database"
        private var instance: TransactionDatabase? = null

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
        private fun buildDatabase(context: Context) = Room.databaseBuilder(context,
            TransactionDatabase::class.java,
            DATABASE_NAME)
            .addMigrations(MIGRATION_4_5)
            .build()
    }
}