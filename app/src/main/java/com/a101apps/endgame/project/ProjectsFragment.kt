package com.a101apps.endgame.project

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.a101apps.endgame.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ProjectsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var projectDatabase: ProjectDatabase
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_project_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rvProjectList)
        projectDatabase = ProjectDatabase.getDatabase(requireContext())
        setupRecyclerView()

        view.findViewById<ExtendedFloatingActionButton>(R.id.btnAddProject).setOnClickListener {
            showAddProjectDialog()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        projectAdapter = ProjectAdapter(emptyList(),
            onEdit = { uuid -> showEditProjectDialog(uuid) },
            onDelete = { project -> showDeleteConfirmationDialog(project) },
            onItemClick = { project ->
                // Using Safe Args to navigate to Project1Fragment with project UUID
                val action = ProjectsFragmentDirections.actionProjectsFragmentToProject1Fragment(projectUuid = project.uuid)
                findNavController().navigate(action)
            }
        )
        recyclerView.adapter = projectAdapter
        loadProjects()
    }

    private fun showAddProjectDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_project, null)
        val projectNameInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectName)
        val projectDescriptionInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectDescription)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Project")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val name = projectNameInput.text.toString().trim()
                val description = projectDescriptionInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    val project = Project(UUID.randomUUID().toString(), name, description)
                    insertProject(project)
                } else {
                    Toast.makeText(requireContext(), "Project name cannot be empty", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun showEditProjectDialog(uuid: String) {
        // Inflate the dialog view
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_project, null)
        val projectNameInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectName)
        val projectDescriptionInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectDescription)

        // Prepare the dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Project")
            .setView(dialogView)
            .setPositiveButton("Update", null)  // We'll set the click listener later to prevent auto-dismissal
            .setNegativeButton("Cancel", null)
            .create()

        // Show the dialog first
        dialog.show()

        // Set click listeners for dialog buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = projectNameInput.text.toString().trim()
            val description = projectDescriptionInput.text.toString().trim()

            if (name.isNotEmpty()) {
                val updatedProject = Project(uuid, name, description)
                updateProject(updatedProject)
                dialog.dismiss()  // Dismiss the dialog manually
            } else {
                Toast.makeText(requireContext(), "Project name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        // Fetch the project data and populate the dialog fields
        projectDatabase.projectDao().getProjectByUuid(uuid).observe(viewLifecycleOwner) { project ->
            if (project != null) {
                projectNameInput.setText(project.name)
                projectDescriptionInput.setText(project.description)
            }
        }
    }

    private fun insertProject(project: Project) {
        lifecycleScope.launch(Dispatchers.IO) {
            projectDatabase.projectDao().insertProject(project)
            loadProjects()
        }
    }

    private fun updateProject(project: Project) {
        lifecycleScope.launch(Dispatchers.IO) {
            projectDatabase.projectDao().updateProject(project)
            loadProjects()
        }
    }

    private fun showDeleteConfirmationDialog(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Project")
            .setMessage("Are you sure you want to delete this project?")
            .setPositiveButton("Delete") { dialog, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    projectDatabase.projectDao().deleteProject(project)
                    loadProjects()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun loadProjects() {
        lifecycleScope.launch {
            projectDatabase.projectDao().getAllProjects().observe(viewLifecycleOwner) { projects ->
                projectAdapter.updateData(projects)
            }
        }
    }

}

class ProjectAdapter(
    private var projectList: List<Project>,
    private val onEdit: (String) -> Unit,
    private val onDelete: (Project) -> Unit,
    private val onItemClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
        val tvProjectName: TextView = itemView.findViewById(R.id.tvProjectName)
        val tvProjectDetails: TextView = itemView.findViewById(R.id.tvProjectDetails)
        val menuMore: ImageView = itemView.findViewById(R.id.menuMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projectList[position]
        holder.tvNumber.text = (position + 1).toString()
        holder.tvProjectName.text = project.name
        holder.tvProjectDetails.text = project.description

        // Set the onClickListener for the itemView
        holder.itemView.setOnClickListener {
            onItemClick(project)
        }

        // Set the color of the menu icon based on the theme
        val iconColor = ContextCompat.getColor(holder.itemView.context, R.color.icon_color)
        holder.menuMore.setColorFilter(iconColor)

        // Set the onClickListener for the menu icon
        holder.menuMore.setOnClickListener { view ->
            showPopupMenu(view, project)
        }
    }

    override fun getItemCount() = projectList.size

    private fun showPopupMenu(view: View, project: Project) {
        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.option_menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit -> {
                    onEdit(project.uuid)
                    true
                }
                R.id.delete -> {
                    onDelete(project)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun updateData(newProjects: List<Project>) {
        this.projectList = newProjects // Use projectList here instead of projects
        notifyDataSetChanged()
    }

}
