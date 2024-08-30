package com.a101apps.endgame.project

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import java.util.Date
import java.util.UUID

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE uuid = :uuid")
    suspend fun getProjectByUuidSync(uuid: String): Project?

    @Insert
    suspend fun insertProject(project: Project)
    @Delete
    suspend fun deleteProject(project: Project)
    @Update
    suspend fun updateProject(project: Project)
    @Query("SELECT * FROM projects WHERE uuid = :uuid")
    fun getProjectByUuid(uuid: String): LiveData<Project?>

    @Query("SELECT * FROM projects")
    fun getAllProjects(): LiveData<List<Project>>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsNoLiveData(): List<Project>
}

@Database(entities = [Project::class, ProjectDetail::class, Todo::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class) // Apply the Type Converters
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectDetailDao(): ProjectDetailDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: ProjectDatabase? = null

        fun getDatabase(context: Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProjectDatabase::class.java,
                    "project_database"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Enable WAL
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}

@Entity(
    tableName = "project_details",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = arrayOf("uuid"),
            childColumns = arrayOf("projectUuid"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProjectDetail(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val projectUuid: String,
    val parentUuid: String? = null,
    val name: String,
    val detail: String
)

@Dao
interface ProjectDetailDao {
    @Insert
    suspend fun insertDetail(detail: ProjectDetail)

    @Query("SELECT * FROM project_details WHERE uuid = :uuid")
    suspend fun getDetailByUuidSync(uuid: String): ProjectDetail?

    @Update
    suspend fun updateDetail(detail: ProjectDetail)

    @Delete
    suspend fun deleteDetail(detail: ProjectDetail)

    @Query("SELECT * FROM project_details WHERE parentUuid = :parentUuid")
    fun getDetailsByParentUuid(parentUuid: String): LiveData<List<ProjectDetail>>

    // Add this method to fetch details by project UUID
    @Query("SELECT * FROM project_details WHERE projectUuid = :projectUuid")
    fun getDetailsByProjectUuid(projectUuid: String): LiveData<List<ProjectDetail>>

    @Query("SELECT * FROM projects WHERE uuid = :uuid")
    fun getProjectByUuid(uuid: String): LiveData<Project?>

    @Query("SELECT * FROM project_details WHERE uuid = :uuid")
    fun getDetailByUuid(uuid: String): LiveData<ProjectDetail?>

    @Query("SELECT * FROM project_details")
    suspend fun getAllProjectDetails(): List<ProjectDetail>
}

@Entity(tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = arrayOf("uuid"),
            childColumns = arrayOf("projectUuid"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectDetail::class,
            parentColumns = arrayOf("uuid"),
            childColumns = arrayOf("projectDetailUuid"),
            onDelete = ForeignKey.SET_NULL
        )
    ])
data class Todo(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val projectUuid: String,
    val projectDetailUuid: String? = null, // Nullable, since a todo might not be associated with a project detail
    var name: String?,
    val addedDate: Date,
    var isCompleted: Boolean
)

class Converters {
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}

@Dao
interface TodoDao {
    @Insert
    suspend fun insertTodo(todo: Todo)

    @Query("SELECT * FROM todos WHERE uuid = :uuid LIMIT 1")
    suspend fun getTodoById(uuid: String): Todo?

    @Update
    suspend fun updateTodo(todo: Todo)

    @Delete
    suspend fun deleteTodo(todo: Todo)

    @Query("SELECT * FROM todos WHERE projectUuid = :projectUuid AND projectDetailUuid IS NULL")
    fun getTodosByProjectUuid(projectUuid: String): LiveData<List<Todo>>

    @Query("SELECT * FROM todos WHERE projectDetailUuid = :projectDetailUuid")
    fun getTodosByProjectDetailUuid(projectDetailUuid: String): LiveData<List<Todo>>

    @Query("SELECT * FROM todos")
    suspend fun getAllTodos(): List<Todo>

}
