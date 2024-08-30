package com.a101apps.endgame.todo

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import java.util.Calendar
import java.util.Date
import java.util.UUID

@Entity
data class Task(
    @PrimaryKey val uuid: UUID = UUID.randomUUID(),
    var name: String,
    var timeFrame: TimeFrame,
    var frequency: Frequency,
    var addedDate: Date,
    var addedTimeFrameStartDate: Date? = null,
    var addedTimeFrameEndDate: Date? = null,
    @ColumnInfo(name = "completed_dates")
    var completedDates: MutableList<Long>? = mutableListOf(),
    var tag: String? = null,
    var isCompleted: Boolean = false,
    var orderNumber: Int? = null
)

data class DatePair(val startDate: Date, val endDate: Date)

enum class Frequency {
    ONCE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM
}

enum class TimeFrame {
    DAY, WEEK, MONTH, YEAR
}

object DateUtils {
    fun getWeekRange(date: Date): DatePair {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.firstDayOfWeek = Calendar.MONDAY

        // Set the calendar to the start of the week
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // Move to the end of the week
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return DatePair(startDate, endDate)
    }

    fun getMonthRange(date: Date): DatePair {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.SECOND, -1) // Adjust to the last moment of the current month
        val endDate = calendar.time

        return DatePair(startDate, endDate)
    }

    fun getYearRange(date: Date): DatePair {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.add(Calendar.YEAR, 1)
        calendar.add(Calendar.SECOND, -1) // Adjust to the last moment of the current year
        val endDate = calendar.time

        return DatePair(startDate, endDate)
    }

    fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

}

class Converters {
    @TypeConverter
    fun fromTimestampList(value: MutableList<Long>?): String? {
        return value?.joinToString(separator = ",")
    }

    @TypeConverter
    fun toTimestampList(value: String?): MutableList<Long>? {
        // Check for null or blank strings before processing
        if (value.isNullOrBlank()) {
            return mutableListOf()
        }
        // Safely parse each element to Long, handling NumberFormatException
        return value.split(",").mapNotNull { element ->
            try {
                element.toLong()
            } catch (e: NumberFormatException) {
                null // Ignore elements that cannot be parsed to Long
            }
        }.toMutableList()
    }

    @TypeConverter
    fun fromDate(value: Date?): Long? = value?.time

    @TypeConverter
    fun toDate(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(uuid: String?): UUID? = uuid?.let { UUID.fromString(it) }
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long
    @Update
    suspend fun updateTask(task: Task)
    @Delete
    suspend fun deleteTask(task: Task)
    @Query("SELECT * FROM Task WHERE uuid = :uuid")
    suspend fun getTask(uuid: UUID): Task?
    @Query("SELECT * FROM Task")
    suspend fun getAllTasks(): List<Task>

}

class TaskCache {
    private val cache = mutableMapOf<String, List<Task>>()

    fun getTasksForDate(date: String): List<Task>? {
        return cache[date]
    }

    fun cacheTasks(date: String, tasks: List<Task>) {
        cache[date] = tasks
    }
}

@Database(entities = [Task::class], version = 1)
@TypeConverters(Converters::class)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

