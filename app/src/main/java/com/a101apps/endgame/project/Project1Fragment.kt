package com.a101apps.endgame.project

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.a101apps.endgame.R
import com.a101apps.endgame.databinding.FragmentProject1Binding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

class Project1Fragment : Fragment() {

    private var _binding: FragmentProject1Binding? = null
    private val binding get() = _binding!!

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddFolder: ExtendedFloatingActionButton
    private lateinit var fabAddTodo: ExtendedFloatingActionButton
    private lateinit var projectDatabase: ProjectDatabase
    private lateinit var detailAdapter: DetailAdapter
    private var projectUuid: String? = null
    private var parentDetailUuid: String? = null // New variable to hold the UUID of the parent detail
    private lateinit var llTodoContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true) // Add this line to indicate that the fragment has an options menu
        _binding = FragmentProject1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.toolbar_menu, menu) // Inflate your menu resource

        // Set the color of the menu icon based on the theme
        val iconColor = ContextCompat.getColor(requireContext(), R.color.icon_color)
        menu.findItem(R.id.action_settings)?.icon?.setTint(iconColor)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val action = Project1FragmentDirections.actionProject1FragmentToSettingsFragment()
                findNavController().navigate(action)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.rvFolders)
        fabAddFolder = view.findViewById(R.id.fabAddFolder)
        fabAddTodo = view.findViewById(R.id.fabAddTodo)
        projectDatabase = ProjectDatabase.getDatabase(requireContext())
        llTodoContainer = view.findViewById(R.id.llTodoContainer)

        arguments?.let {
            val safeArgs = Project1FragmentArgs.fromBundle(it)
            projectUuid = safeArgs.projectUuid
            parentDetailUuid = safeArgs.parentDetailUuid // Initialize from arguments
        }

        val progressBarFolders: ProgressBar = view.findViewById(R.id.progressBarFolders)
        val progressBarTodos: ProgressBar = view.findViewById(R.id.progressBarTodos)

        // Start by setting the progress bars to visible, indicating loading state
        progressBarFolders.visibility = View.VISIBLE
        progressBarTodos.visibility = View.VISIBLE

        setupToolbar()
        setupRecyclerView()
        setupFabs()
        updateBreadcrumb()
        loadTodos()
        observeTodos()
    }

    private fun setupToolbar() {
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Check if there's a parent detail UUID, indicating we are viewing a project detail
        parentDetailUuid?.let { uuid ->
            projectDatabase.projectDetailDao().getDetailByUuid(uuid).observe(viewLifecycleOwner) { detail ->
                detail?.let {
                    (activity as AppCompatActivity).supportActionBar?.title = detail.name
                }
            }
        } ?: projectUuid?.let { uuid ->
            // If there's no parent detail UUID, we are viewing the project itself
            projectDatabase.projectDao().getProjectByUuid(uuid).observe(viewLifecycleOwner) { project ->
                project?.let {
                    (activity as AppCompatActivity).supportActionBar?.title = project.name
                }
            }
        }
    }

    private fun updateBreadcrumb() {
        lifecycleScope.launch {
            val segments = mutableListOf<String>() // List to hold the names in hierarchical order
            appendBreadcrumbSegments(segments, parentDetailUuid)
            val breadcrumbText = segments.joinToString(" > ")
            withContext(Dispatchers.Main) {
                view?.findViewById<TextView>(R.id.breadcrumbTextView)?.text = breadcrumbText
            }
        }
    }

    private suspend fun appendBreadcrumbSegments(segments: MutableList<String>, detailUuid: String?) {
        if (detailUuid == null) {
            projectUuid?.let { uuid ->
                val project = withContext(Dispatchers.IO) {
                    projectDatabase.projectDao().getProjectByUuidSync(uuid)
                }
                project?.name?.let { projectName ->
                    segments.add(0, projectName) // Prepend the project name at the beginning
                }
            }
        } else {
            val detail = withContext(Dispatchers.IO) {
                projectDatabase.projectDetailDao().getDetailByUuidSync(detailUuid)
            }
            detail?.let {
                segments.add(0, it.name) // Prepend the detail name to maintain order
                appendBreadcrumbSegments(segments, it.parentUuid) // Recursive call with parentUuid
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        detailAdapter = DetailAdapter(emptyList(),
            onEdit = { detail -> showEditDetailDialog(detail) },
            onDelete = { detail -> showDeleteConfirmationDialog(detail) },
            onItemClick = { detail ->
                val bundle = Bundle().apply {
                    putString("projectUuid", projectUuid)
                    putString("parentDetailUuid", detail.uuid)
                }
                findNavController().navigate(R.id.action_project1Fragment_self, bundle)
            }
        )
        recyclerView.adapter = detailAdapter
        loadDetails()
    }

    private fun loadDetails() {
        // Assume loading has started, show progress bar
        view?.findViewById<ProgressBar>(R.id.progressBarFolders)?.visibility = View.VISIBLE

        parentDetailUuid?.let { uuid ->
            projectDatabase.projectDetailDao().getDetailsByParentUuid(uuid).observe(viewLifecycleOwner) { details ->
                detailAdapter.updateData(details)
                view?.findViewById<ProgressBar>(R.id.progressBarFolders)?.visibility = View.GONE
                view?.findViewById<TextView>(R.id.tvFolderHeading)?.visibility = if (details.isNotEmpty()) View.VISIBLE else View.GONE
            }
        } ?: projectUuid?.let { uuid ->
            projectDatabase.projectDetailDao().getDetailsByProjectUuid(uuid).observe(viewLifecycleOwner) { details ->
                val topLevelDetails = details.filter { it.parentUuid.isNullOrEmpty() }
                detailAdapter.updateData(topLevelDetails)
                view?.findViewById<ProgressBar>(R.id.progressBarFolders)?.visibility = View.GONE
                view?.findViewById<TextView>(R.id.tvFolderHeading)?.visibility = if (topLevelDetails.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showEditDetailDialog(detail: ProjectDetail) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_project, null)
        val detailNameInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectName)
        val detailDescriptionInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectDescription)

        // Pre-fill the dialog with the existing detail data
        detailNameInput.setText(detail.name)
        detailDescriptionInput.setText(detail.detail)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Detail")
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                val newName = detailNameInput.text.toString().trim()
                val newDescription = detailDescriptionInput.text.toString().trim()
                val updatedDetail = detail.copy(name = newName, detail = newDescription)

                // Update the detail in the database
                CoroutineScope(Dispatchers.IO).launch {
                    projectDatabase.projectDetailDao().updateDetail(updatedDetail)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(detail: ProjectDetail) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Detail")
            .setMessage("Are you sure you want to delete this detail?")
            .setPositiveButton("Delete") { dialog, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    projectDatabase.projectDetailDao().deleteDetail(detail)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddDetailDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_project, null)
        val detailNameInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectName)
        val detailDescriptionInput = dialogView.findViewById<TextInputEditText>(R.id.etProjectDescription)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Detail")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val name = detailNameInput.text.toString().trim()
                val description = detailDescriptionInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    val newDetail = ProjectDetail(
                        uuid = UUID.randomUUID().toString(),
                        projectUuid = projectUuid ?: "",
                        parentUuid = parentDetailUuid, // Associate with current parentDetailUuid
                        name = name,
                        detail = description
                    )
                    insertDetail(newDetail)
                } else {
                    Toast.makeText(requireContext(), "Detail name cannot be empty", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun insertDetail(detail: ProjectDetail) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("Project1Fragment", "Inserting new detail: ${detail.name}")
            projectDatabase.projectDetailDao().insertDetail(detail)
            withContext(Dispatchers.Main) {
                // Once the detail has been successfully added, update the breadcrumb on the UI thread
                updateBreadcrumb()
            }
        }
    }

    private fun setupFabs() {
        fabAddFolder.setOnClickListener {
            showAddDetailDialog()
        }
        fabAddTodo.setOnClickListener {
            addTodoItem()
        }
    }

    private fun openKeyboardFor(editText: EditText) {
        editText.post {
            editText.requestFocus()
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun handleTodoInteractions(todoView: View, todo: Todo, isNew: Boolean = false) {
        val etTodoName: EditText = todoView.findViewById(R.id.etTodoName)
        val cbTodo: CheckBox = todoView.findViewById(R.id.cbTodo)

        // Initially set the checkbox state and text appearance based on the todo's completion status
        cbTodo.isChecked = todo.isCompleted
        updateTodoAppearance(todo.isCompleted, etTodoName)

        // Listener for when the user finishes editing the todo name
        etTodoName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                handleTodoNameFocusLost(etTodoName, todo, isNew, todoView)
            }
        }

        // Listener for changes in the todo completion status
        cbTodo.setOnCheckedChangeListener { _, isChecked ->
            todo.isCompleted = isChecked
            updateTodoAppearance(isChecked, etTodoName)

            // Save the todo if the checkbox state changes, regardless of the todo's newness or name emptiness
            saveTodoInDatabase(todo)
        }
    }

    private fun handleTodoNameFocusLost(etTodoName: EditText, todo: Todo, isNew: Boolean, todoView: View) {
        val newName = etTodoName.text.toString().trim()
        // Allow empty TODOs to exist by removing the check for empty names and not removing the view
        if (isNew || newName != todo.name) {
            todo.name = newName
            saveTodoInDatabase(todo)
        }
    }

    private fun updateTodoAppearance(isCompleted: Boolean, todoNameEditText: EditText) {
        if (isCompleted) {
            todoNameEditText.paintFlags = todoNameEditText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            todoNameEditText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        } else {
            todoNameEditText.paintFlags = todoNameEditText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            val textColorPrimary = TypedValue().also {
                requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, it, true)
            }.resourceId
            todoNameEditText.setTextColor(ContextCompat.getColor(requireContext(), textColorPrimary))
        }
    }

    private fun loadTodos() {
        view?.findViewById<ProgressBar>(R.id.progressBarTodos)?.visibility = View.VISIBLE

        val todosLiveData = if (parentDetailUuid != null) {
            // Load todos associated with a specific ProjectDetail
            projectDatabase.todoDao().getTodosByProjectDetailUuid(parentDetailUuid!!)
        } else {
            // Load todos directly associated with the project (no specific ProjectDetail)
            projectDatabase.todoDao().getTodosByProjectUuid(projectUuid!!)
        }

        todosLiveData.observe(viewLifecycleOwner) { todos ->
            displayTodos(todos)
            view?.findViewById<ProgressBar>(R.id.progressBarTodos)?.visibility = View.GONE
            view?.findViewById<TextView>(R.id.tvTodoHeading)?.visibility = if (todos.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun saveTodoInDatabase(todo: Todo) {
        lifecycleScope.launch(Dispatchers.IO) {
            projectDatabase.todoDao().getTodoById(todo.uuid)?.let {
                val updatedTodo = it.copy(name = todo.name, isCompleted = todo.isCompleted, projectDetailUuid = todo.projectDetailUuid)
                projectDatabase.todoDao().updateTodo(updatedTodo)
            } ?: run {
                projectDatabase.todoDao().insertTodo(todo)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        observeTodos()
    }

    private fun observeTodos() {
        val todosLiveData = if (parentDetailUuid != null) {
            projectDatabase.todoDao().getTodosByProjectDetailUuid(parentDetailUuid!!)
        } else {
            projectDatabase.todoDao().getTodosByProjectUuid(projectUuid!!)
        }

        todosLiveData.removeObservers(viewLifecycleOwner)
        todosLiveData.observe(viewLifecycleOwner) { todos ->
            displayTodos(todos)
        }
    }

    private fun displayTodos(todos: List<Todo>) {
        val existingTodos = mutableSetOf<String>()

        // Collect the UUIDs of currently displayed TODOs
        for (i in 0 until llTodoContainer.childCount) {
            val todoView = llTodoContainer.getChildAt(i)
            val etTodoName: EditText = todoView.findViewById(R.id.etTodoName)
            val todoUuid = todoView.tag as? String
            todoUuid?.let { existingTodos.add(it) }
        }

        todos.forEachIndexed { index, todo ->
            if (!existingTodos.contains(todo.uuid)) {
                val todoView = createTodoView(todo, isNew = false, index = index + 1) // Pass index + 1 for correct numbering
                todoView.tag = todo.uuid
                llTodoContainer.addView(todoView)
            }
        }
    }

    private fun createTodoView(todo: Todo, isNew: Boolean = false, index: Int? = null): View {
        // Inflate the layout for the todo item
        val todoView = LayoutInflater.from(requireContext()).inflate(R.layout.item_todo, llTodoContainer, false)

        // Find the views within the inflated layout
        val etTodoName: EditText = todoView.findViewById(R.id.etTodoName)
        val cbTodo: CheckBox = todoView.findViewById(R.id.cbTodo)
        val tvTodoNumber: TextView = todoView.findViewById(R.id.tvTodoNumber) // TextView for displaying the todo's sequence number

        // Set the sequence number for the todo item, if an index is provided
        index?.let {
            tvTodoNumber.text = "${it}."
        }

        etTodoName.setText(todo.name)
        cbTodo.isChecked = todo.isCompleted

        // Update the appearance of the todo item based on its completion status
        updateTodoAppearance(todo.isCompleted, etTodoName)

        // Handle interactions with the todo item (editing the name, toggling completion status)
        handleTodoInteractions(todoView, todo, isNew)

        val ivMore: ImageView = todoView.findViewById(R.id.ivTodoMenu) // Kebab menu icon

        // Set the color of the kebab menu icon to adapt to light/dark mode
        ivMore.setColorFilter(ContextCompat.getColor(requireContext(), R.color.icon_color)) // Use ContextCompat to retrieve the color

        ivMore.setOnClickListener { view ->
            // Implement showing a context menu or popup menu for the todo item
            showPopupMenu(view, todo)
        }

        return todoView
    }

    private fun addTodoItem() {
        val newTodo = Todo(
            uuid = UUID.randomUUID().toString(),
            projectUuid = projectUuid ?: "",
            projectDetailUuid = parentDetailUuid,
            name = "",
            addedDate = Date(),
            isCompleted = false
        )

        // The new todo's number is the current count of todos in the container plus one
        val todoNumber = llTodoContainer.childCount + 1

        val todoView = createTodoView(newTodo, isNew = true, index = todoNumber)
        todoView.tag = newTodo.uuid
        llTodoContainer.addView(todoView)

        // Focus the EditText for the todo name
        val etTodoName: EditText = todoView.findViewById(R.id.etTodoName)
        etTodoName.requestFocus()
        etTodoName.postDelayed({ openKeyboardFor(etTodoName) }, 100)
    }

    private fun showPopupMenu(view: View, todo: Todo) {
        val popup = PopupMenu(requireContext(), view)
        popup.inflate(R.menu.todo_option_menu) // Your menu XML with the delete option
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete -> {
                    deleteTodo(todo)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // Modify deleteTodo to call refreshTodoUI after deletion
    private fun deleteTodo(todo: Todo) {
        lifecycleScope.launch(Dispatchers.IO) {
            projectDatabase.todoDao().deleteTodo(todo)
            withContext(Dispatchers.Main) {
                // Load todos again to refresh the UI
                loadTodosafterdeleted()
            }
        }
    }

    // Adjust loadTodos to observe todos and use refreshTodoUI for displaying them
    private fun loadTodosafterdeleted() {
        view?.findViewById<ProgressBar>(R.id.progressBarTodos)?.visibility = View.VISIBLE

        val todosLiveData = if (parentDetailUuid != null) {
            // Load todos associated with a specific ProjectDetail
            projectDatabase.todoDao().getTodosByProjectDetailUuid(parentDetailUuid!!)
        } else {
            // Load todos directly associated with the project (no specific ProjectDetail)
            projectDatabase.todoDao().getTodosByProjectUuid(projectUuid!!)
        }

        todosLiveData.observe(viewLifecycleOwner) { todos ->
            refreshTodoUI(todos)
            view?.findViewById<ProgressBar>(R.id.progressBarTodos)?.visibility = View.GONE
            view?.findViewById<TextView>(R.id.tvTodoHeading)?.visibility = if (todos.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
    private fun refreshTodoUI(todos: List<Todo>) {
        val existingTodos = mutableSetOf<String>()

        // Collect the UUIDs of currently displayed TODOs
        for (i in 0 until llTodoContainer.childCount) {
            val todoView = llTodoContainer.getChildAt(i)
            val etTodoName: EditText = todoView.findViewById(R.id.etTodoName)
            val todoUuid = todoView.tag as? String
            todoUuid?.let { existingTodos.add(it) }
        }

        // Remove views for todos that are no longer in the list
        for (i in (llTodoContainer.childCount - 1) downTo 0) {
            val todoView = llTodoContainer.getChildAt(i)
            val todoUuid = todoView.tag as? String
            if (todoUuid != null && todos.none { it.uuid == todoUuid }) {
                llTodoContainer.removeViewAt(i)
            }
        }

        // Add views for new todos
        todos.forEachIndexed { index, todo ->
            if (!existingTodos.contains(todo.uuid)) {
                val todoView = createTodoView(todo, isNew = false, index = index + 1) // Pass index + 1 for correct numbering
                todoView.tag = todo.uuid
                llTodoContainer.addView(todoView)
            }
        }
    }

}

class DetailAdapter(
    private var detailList: List<ProjectDetail>,
    private val onEdit: (ProjectDetail) -> Unit,
    private val onDelete: (ProjectDetail) -> Unit,
    private val onItemClick: (ProjectDetail) -> Unit
) : RecyclerView.Adapter<DetailAdapter.DetailViewHolder>() {

    class DetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
        val tvDetailName: TextView = itemView.findViewById(R.id.tvProjectName)
        val tvDetailDescription: TextView = itemView.findViewById(R.id.tvProjectDetails)
        val menuMore: ImageView = itemView.findViewById(R.id.menuMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return DetailViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        val detail = detailList[position]

        // Add a full stop after the number and a space for better readability
        holder.tvNumber.text = "${position + 1}."

        holder.tvDetailName.text = detail.name
        holder.tvDetailDescription.text = detail.detail

        // Set the onClickListener for the itemView
        holder.itemView.setOnClickListener {
            onItemClick(detail)
        }

        // Set the color of the menu icon based on the theme
        val iconColor = ContextCompat.getColor(holder.itemView.context, R.color.icon_color)
        holder.menuMore.setColorFilter(iconColor)

        // Set the onClickListener for the menu icon
        holder.menuMore.setOnClickListener { view ->
            showPopupMenu(view, detail)
        }
    }

    override fun getItemCount() = detailList.size

    private fun showPopupMenu(view: View, detail: ProjectDetail) {
        val popup = PopupMenu(view.context, view)
        popup.inflate(R.menu.option_menu) // Make sure you have 'option_menu' in your 'menu' resource folder
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit -> {
                    onEdit(detail)
                    true
                }
                R.id.delete -> {
                    onDelete(detail)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun updateData(newDetails: List<ProjectDetail>) {
        this.detailList = newDetails
        notifyDataSetChanged()
    }
}
