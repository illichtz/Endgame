package com.a101apps.endgame

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.a101apps.endgame.project.Project
import com.a101apps.endgame.project.ProjectDatabase
import com.a101apps.endgame.project.ProjectDetail
import com.a101apps.endgame.project.Todo
import com.a101apps.endgame.todo.Task
import com.a101apps.endgame.todo.TaskDatabase
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        setupExportButton(view)
        progressBar = view.findViewById(R.id.progressBar)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        setupToolbar()
    }

    private fun setupToolbar() {
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupExportButton(view: View) {
        view.findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportDataToCSV()
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun formatTimestamp(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
    }

    private fun exportDataToCSV() {
        coroutineScope.launch {
            showLoading(true)

            val dbProjects = ProjectDatabase.getDatabase(requireContext())
            val dbTasks = TaskDatabase.getInstance(requireContext())

            // Fetch data concurrently using async
            val tasksDeferred = async(Dispatchers.IO) { dbTasks.taskDao().getAllTasks() }
            val projectsDeferred = async(Dispatchers.IO) { dbProjects.projectDao().getAllProjectsNoLiveData() }
            val projectDetailsDeferred = async(Dispatchers.IO) { dbProjects.projectDetailDao().getAllProjectDetails() }
            val todosDeferred = async(Dispatchers.IO) { dbProjects.todoDao().getAllTodos() }

            // Wait for all data to be fetched
            val tasks = tasksDeferred.await()
            val projects = projectsDeferred.await()
            val projectDetails = projectDetailsDeferred.await()
            val todos = todosDeferred.await()

            val csvDataProjects = try {
                convertProjectsToCSV(projects, projectDetails, todos)
            } catch (e: Exception) {
                Log.e("CSVExport", "Error converting project data to CSV: ${e.message}", e)
                null // Return null to indicate failure
            }

            val csvDataTasks = convertTasksToCSV(tasks)

            withContext(Dispatchers.IO) {
                try {
                    val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsPath.exists() && !downloadsPath.mkdirs()) {
                        throw IOException("Cannot create or access the Downloads directory")
                    }

                    // Get current date and time
                    val dateFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                    val currentTime = dateFormat.format(Date())

                    // Create file names with date and time
                    val fileProjects = File(downloadsPath, "Projects_export_$currentTime.csv")
                    val fileTasks = File(downloadsPath, "Tasks_export_$currentTime.csv")

                    csvDataProjects?.let {
                        fileProjects.writeText(it)
                    }
                    fileTasks.writeText(csvDataTasks)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Exported to Downloads: ${fileProjects.name} and ${fileTasks.name}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to export data. Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                    }
                }
            }
        }
    }

    private fun convertProjectsToCSV(
        projects: List<Project>,
        projectDetails: List<ProjectDetail>,
        todos: List<Todo>
    ): String {
        val header = "Project UUID,Project Name,Project Description,Detail UUID,Detail Name,Detail Description,Todo UUID,Todo Name,Todo Added Date,Todo Is Completed\n"
        val csvBuilder = StringBuilder()
        csvBuilder.append(header)

        val detailsMap = projectDetails.groupBy { it.projectUuid }
        val todosMapByProject = todos.groupBy { it.projectUuid }
        val todosMapByDetail = todos.groupBy { it.projectDetailUuid }

        for (project in projects) {
            val projectUuid = project.uuid
            val details = detailsMap[projectUuid] ?: emptyList()
            val projectTodos = todosMapByProject[projectUuid] ?: emptyList()

            Log.d("CSVExport", "Processing project: ${project.name} with UUID: $projectUuid")

            if (details.isEmpty() && projectTodos.isEmpty()) {
                csvBuilder.append("${project.uuid},${project.name},${project.description},,,,,,\n")
            } else {
                for (detail in details) {
                    val detailTodos = todosMapByDetail[detail.uuid] ?: emptyList()

                    Log.d("CSVExport", "Processing detail: ${detail.name} with UUID: ${detail.uuid} for project UUID: $projectUuid")

                    if (detailTodos.isEmpty()) {
                        csvBuilder.append("${project.uuid},${project.name},${project.description},${detail.uuid},${detail.name},${detail.detail},,,,\n")
                    } else {
                        for (todo in detailTodos) {
                            Log.d("CSVExport", "Processing todo: ${todo.name} with UUID: ${todo.uuid} for detail UUID: ${detail.uuid}")

                            csvBuilder.append("${project.uuid},${project.name},${project.description},${detail.uuid},${detail.name},${detail.detail},${todo.uuid},${todo.name},${todo.addedDate},${todo.isCompleted}\n")
                        }
                    }
                }

                for (todo in projectTodos) {
                    Log.d("CSVExport", "Processing todo: ${todo.name} with UUID: ${todo.uuid} for project UUID: $projectUuid")

                    csvBuilder.append("${project.uuid},${project.name},${project.description},,,,${todo.uuid},${todo.name},${todo.addedDate},${todo.isCompleted}\n")
                }
            }
        }

        Log.d("CSVExport", "CSV generation completed")
        Log.d("1", "Projects: $projects")
        Log.d("2", "Project Details: $projectDetails")
        Log.d("3", "Todos: $todos")

        return csvBuilder.toString()
    }

    private fun convertTasksToCSV(tasks: List<Task>): String {
        val header = "UUID,Name,TimeFrame,Frequency,AddedDate,CompletedDates,Tag,IsCompleted,OrderNumber\n"
        return tasks.joinToString(
            separator = "\n",
            prefix = header
        ) { task ->
            val name = "\"${task.name.replace("\"", "\"\"")}\""
            val completedDates = task.completedDates?.joinToString(separator = "|") ?: ""
            "${task.uuid},$name,${task.timeFrame},${task.frequency},${formatTimestamp(task.addedDate)},$completedDates,${task.tag},${task.isCompleted},${task.orderNumber}"
        }
    }


}
