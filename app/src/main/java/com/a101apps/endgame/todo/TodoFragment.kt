package com.a101apps.endgame.todo

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.a101apps.endgame.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class TodoFragment : Fragment() {

    private lateinit var dateTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tasksContainer: LinearLayout
    private lateinit var headingTextView: TextView
    private lateinit var addTodoFab: ExtendedFloatingActionButton
    private var selectedDate = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd MMM, EEEE", Locale.getDefault())
    private var currentFilter = "Day"
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var chipDay: Chip
    private lateinit var chipWeek: Chip
    private lateinit var chipMonth: Chip
    private lateinit var chipYear: Chip
    private val isNewTaskMap = mutableMapOf<UUID, Boolean>()
    private lateinit var textViewDay: TextView
    private lateinit var textViewWeek: TextView
    private lateinit var textViewMonth: TextView
    private lateinit var textViewYear: TextView
    private var focusedTask: Task? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_todo, container, false)
        setupUIComponents(view)
        setupAddTodoButton(view)
        return view
    }

    private fun updateTaskCountViews() {
        // Initially set placeholders
        textViewDay.text = "x/x"
        textViewWeek.text = "x/x"
        textViewMonth.text = "x/x"
        textViewYear.text = "x/x"

        lifecycleScope.launch(Dispatchers.IO) {
            val allTasks = TaskDatabase.getInstance(requireContext()).taskDao().getAllTasks()

            // Ensure selectedDate is set to midnight
            val calendar = Calendar.getInstance().apply {
                time = selectedDate.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val adjustedSelectedDate = calendar.time

            // Define the ranges for the current selection
            val weekRange = DateUtils.getWeekRange(adjustedSelectedDate)
            val monthRange = DateUtils.getMonthRange(adjustedSelectedDate)
            val yearRange = DateUtils.getYearRange(adjustedSelectedDate)

            // Log the week range
            Log.d("WeekRange", "Start: ${weekRange.startDate}, End: ${weekRange.endDate}")

            // Filter tasks for each time frame
            val dayTasks = allTasks.filter { it.timeFrame == TimeFrame.DAY && DateUtils.isSameDay(it.addedDate, adjustedSelectedDate) }
            val weekTasks = allTasks.filter { it.timeFrame == TimeFrame.WEEK && it.addedDate in weekRange.startDate..weekRange.endDate }
            val monthTasks = allTasks.filter { it.timeFrame == TimeFrame.MONTH && it.addedDate in monthRange.startDate..monthRange.endDate }
            val yearTasks = allTasks.filter { it.timeFrame == TimeFrame.YEAR && it.addedDate in yearRange.startDate..yearRange.endDate }

            // Log the tasks in the week range
            weekTasks.forEach { task ->
                Log.d("WeekTask", "Task: ${task.name}, Date: ${task.addedDate}")
            }

            // Aggregate tasks for broader scopes
            val aggregateWeekTasks = allTasks.filter { it.addedDate in weekRange.startDate..weekRange.endDate }
            val aggregateMonthTasks = allTasks.filter { it.addedDate in monthRange.startDate..monthRange.endDate }
            val aggregateYearTasks = allTasks.filter { it.addedDate in yearRange.startDate..yearRange.endDate }

            withContext(Dispatchers.Main) {
                when (currentFilter) {
                    "Day" -> {
                        textViewDay.text = formatTaskCount(dayTasks)
                        textViewWeek.text = formatTaskCount(aggregateWeekTasks.filter { it.timeFrame == TimeFrame.WEEK })
                        textViewMonth.text = formatTaskCount(aggregateMonthTasks.filter { it.timeFrame == TimeFrame.MONTH })
                        textViewYear.text = formatTaskCount(aggregateYearTasks.filter { it.timeFrame == TimeFrame.YEAR })
                    }
                    "Week" -> {
                        textViewDay.text = formatTaskCount(aggregateWeekTasks.filter { it.timeFrame == TimeFrame.DAY }, aggregate = true)
                        textViewWeek.text = formatTaskCount(weekTasks)
                        textViewMonth.text = formatTaskCount(aggregateMonthTasks.filter { it.timeFrame == TimeFrame.MONTH })
                        textViewYear.text = formatTaskCount(aggregateYearTasks.filter { it.timeFrame == TimeFrame.YEAR })
                    }
                    "Month" -> {
                        textViewDay.text = formatTaskCount(aggregateMonthTasks.filter { it.timeFrame == TimeFrame.DAY }, aggregate = true)
                        textViewWeek.text = formatTaskCount(aggregateMonthTasks.filter { it.timeFrame == TimeFrame.WEEK }, aggregate = true)
                        textViewMonth.text = formatTaskCount(monthTasks)
                        textViewYear.text = formatTaskCount(aggregateYearTasks.filter { it.timeFrame == TimeFrame.YEAR })
                    }
                    "Year" -> {
                        textViewDay.text = formatTaskCount(aggregateYearTasks.filter { it.timeFrame == TimeFrame.DAY }, aggregate = true)
                        textViewWeek.text = formatTaskCount(aggregateYearTasks.filter { it.timeFrame == TimeFrame.WEEK }, aggregate = true)
                        textViewMonth.text = formatTaskCount(aggregateYearTasks.filter { it.timeFrame == TimeFrame.MONTH }, aggregate = true)
                        textViewYear.text = formatTaskCount(yearTasks)
                    }
                }
            }
        }
    }

    private fun formatTaskCount(tasks: List<Task>, aggregate: Boolean = false): String {
        val completed = tasks.count { it.isCompleted }
        val total = tasks.size
        return "$completed/$total"
    }

    private fun createTaskCard(): View = layoutInflater.inflate(R.layout.item_todo, tasksContainer, false).apply {
        findViewById<TextView>(R.id.tvTodoNumber).text = (tasksContainer.childCount + 1).toString()
        val taskEditText = findViewById<EditText>(R.id.etTodoName)
        val kebabMenu = findViewById<ImageView>(R.id.ivTodoMenu)
        kebabMenu.setColorFilter(ContextCompat.getColor(requireContext(), R.color.icon_color)) // Corrected method to get color
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUIComponents(view)
        loadTasksForSelectedDate()
        setupAddTodoButton(view)
    }

    private fun setupUIComponents(view: View) {
        // Initialize the views
        dateTextView = view.findViewById(R.id.dateTextView)
        progressBar = view.findViewById(R.id.loadingProgressBar)
        tasksContainer = view.findViewById(R.id.tasksContainer)
        headingTextView = view.findViewById(R.id.headingText)
        addTodoFab = view.findViewById(R.id.fabAddTodo)
        filterChipGroup = view.findViewById(R.id.filterChipGroup)

        // Initialize and configure the chips
        chipDay = view.findViewById(R.id.chipDay)
        chipWeek = view.findViewById(R.id.chipWeek)
        chipMonth = view.findViewById(R.id.chipMonth)
        chipYear = view.findViewById(R.id.chipYear)
        // Existing initializations...
        textViewDay = view.findViewById(R.id.textViewDay)
        textViewWeek = view.findViewById(R.id.textViewWeek)
        textViewMonth = view.findViewById(R.id.textViewMonth)
        textViewYear = view.findViewById(R.id.textViewYear)

        setChipBackgroundColor(chipDay, Color.parseColor("#64B5F6"), Color.parseColor("#E0E0E0"))
        setChipBackgroundColor(chipWeek, Color.parseColor("#81C784"), Color.parseColor("#E0E0E0"))
        setChipBackgroundColor(chipMonth, Color.parseColor("#FFD54F"), Color.parseColor("#E0E0E0"))
        setChipBackgroundColor(chipYear, Color.parseColor("#FF8A65"), Color.parseColor("#E0E0E0"))

        // Set up the chip group with a listener
        setupChipGroup()

        // Other UI initializations
        setupDateNavigation(view)
        updateDateTextAndColor(selectedDate)
        updateTaskCountViews()
    }

    private fun setupChipGroup() {
        filterChipGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.chipDay -> {
                    updateFilter("Day")
                }
                R.id.chipWeek -> {
                    updateFilter("Week")
                }
                R.id.chipMonth -> {
                    updateFilter("Month")
                }
                R.id.chipYear -> {
                    updateFilter("Year")
                }
            }
        }
        // Default selection set to Week
        updateFilter("Day")
        chipDay.isChecked = true
    }

    private fun updateFilter(filter: String) {
        currentFilter = filter
        updateUIForSelectedFilter()
    }

    private fun updateUIForSelectedFilter() {
        updateHeadingBasedOnFilter()
        updateSelectedDateForFilter()
        updateDateTextAndColor(selectedDate)
        loadTasksForSelectedDate()
        updateTaskCountViews()
    }

    private fun updateSelectedDateForFilter() {
        selectedDate = Calendar.getInstance()
        when (currentFilter) {
            "Week" -> selectedDate // Adjust if needed for the week
            "Month" -> selectedDate.apply { set(Calendar.DAY_OF_MONTH, 1) }
            "Year" -> selectedDate.apply { set(Calendar.DAY_OF_YEAR, 1) }
        }
    }

    private fun setChipBackgroundColor(chip: Chip, checkedColor: Int, uncheckedColor: Int) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked), // checked
            intArrayOf(-android.R.attr.state_checked)  // unchecked
        )

        val colors = intArrayOf(checkedColor, uncheckedColor)
        chip.chipBackgroundColor = ColorStateList(states, colors)
    }

    private fun setupDateNavigation(view: View) {
        view.findViewById<Button>(R.id.prevButton).setOnClickListener { changeDate(-1) }
        view.findViewById<Button>(R.id.nextButton).setOnClickListener { changeDate(1) }
        dateTextView.setOnClickListener { showDatePicker() }
    }

    private fun updateHeadingBasedOnFilter() {
        val headingText = when (currentFilter) {
            "Day" -> "To Do Today"
            "Week" -> "To Do This Week"
            "Month" -> "To Do This Month"
            "Year" -> "To Do This Year"
            else -> "To Do"
        }
        headingTextView.text = headingText
    }

    private fun showDatePicker() {
        when (currentFilter) {
            "Day" -> {
                DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                    selectedDate.apply { set(year, month, dayOfMonth) }
                    updateDateTextAndColor(selectedDate)
                    loadTasksForSelectedDate()
                }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
            }
            "Week" -> showWeekPicker()
            "Month" -> showMonthPicker()
            "Year" -> showYearPicker()
        }
    }

    private fun showWeekPicker() {
        Toast.makeText(context, "Week Picker not implemented", Toast.LENGTH_SHORT).show()
    }

    private fun showMonthPicker() {
        Toast.makeText(context, "Month Picker not implemented", Toast.LENGTH_SHORT).show()
    }

    private fun showYearPicker() {
        Toast.makeText(context, "Year Picker not implemented", Toast.LENGTH_SHORT).show()
    }

    private fun updateDateTextAndColor(calendar: Calendar) {
        // Update the date format based on the current filter
        dateTextView.text = when (currentFilter) {
            "Day" -> dateFormat.format(calendar.time)
            "Week" -> getWeekRange(calendar)
            "Month" -> getMonthFormat(calendar)
            "Year" -> getYearFormat(calendar)
            else -> ""
        }

        // Determine the color based on the current filter
        val color = when (currentFilter) {
            "Day", "Week" -> getDateColor(calendar)
            "Month" -> getMonthColor(calendar)
            "Year" -> getYearColor(calendar)
            else -> Color.BLACK // Default color
        }

        dateTextView.setTextColor(color)
    }

    private fun getMonthColor(calendar: Calendar): Int {
        val now = Calendar.getInstance()

        return when {
            calendar.get(Calendar.YEAR) < now.get(Calendar.YEAR) -> ContextCompat.getColor(requireContext(), R.color.red)
            calendar.get(Calendar.YEAR) > now.get(Calendar.YEAR) -> ContextCompat.getColor(requireContext(), R.color.blue)
            calendar.get(Calendar.MONTH) < now.get(Calendar.MONTH) -> ContextCompat.getColor(requireContext(), R.color.red)
            calendar.get(Calendar.MONTH) > now.get(Calendar.MONTH) -> ContextCompat.getColor(requireContext(), R.color.blue)
            else -> ContextCompat.getColor(requireContext(), R.color.green) // This month
        }
    }

    private fun getYearColor(calendar: Calendar): Int {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val selectedYear = calendar.get(Calendar.YEAR)

        return when {
            selectedYear < currentYear -> ContextCompat.getColor(requireContext(), R.color.red) // Past year
            selectedYear > currentYear -> ContextCompat.getColor(requireContext(), R.color.blue) // Future year
            else -> ContextCompat.getColor(requireContext(), R.color.green) // This year
        }
    }
    private fun getDateRangeForFilter(): DatePair {
        val calendar = Calendar.getInstance().apply { time = selectedDate.time }

        return when (currentFilter) {
            "Week" -> {
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.time

                calendar.add(Calendar.DAY_OF_WEEK, 6) // Move to the last day of the week (Sunday)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfWeek = calendar.time

                DatePair(startOfWeek, endOfWeek)
            }
            "Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.time

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfMonth = calendar.time

                DatePair(startOfMonth, endOfMonth)
            }
            "Year" -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYear = calendar.time

                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfYear = calendar.time

                DatePair(startOfYear, endOfYear)
            }
            else -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.time

                calendar.add(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfDay = calendar.time

                DatePair(startOfDay, endOfDay)
            }
        }
    }
    private fun getWeekRange(calendar: Calendar): String {
        val range = DateUtils.getWeekRange(calendar.time)
        val startStr = SimpleDateFormat("d MMM EEE", Locale.getDefault()).format(range.startDate)
        val endStr = SimpleDateFormat("d MMM EEE", Locale.getDefault()).format(range.endDate)
        return "$startStr - $endStr"
    }

    private fun getMonthFormat(calendar: Calendar): String {
        val range = DateUtils.getMonthRange(calendar.time)
        return SimpleDateFormat("MMMM", Locale.getDefault()).format(range.startDate)
    }

    private fun getYearFormat(calendar: Calendar): String {
        val range = DateUtils.getYearRange(calendar.time)
        return SimpleDateFormat("YYYY", Locale.getDefault()).format(range.startDate)
    }

    private fun getDateColor(calendar: Calendar): Int {
        return when {
            calendar.before(Calendar.getInstance().apply { setToMidnight() }) -> ContextCompat.getColor(requireContext(), R.color.red)
            calendar.isSameDay(Calendar.getInstance().apply { setToMidnight() }) -> ContextCompat.getColor(requireContext(), R.color.green)
            else -> ContextCompat.getColor(requireContext(), R.color.blue)
        }
    }

    private fun Calendar.setToMidnight() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun changeDate(amount: Int) {
        val field = when (currentFilter) {
            "Week" -> Calendar.WEEK_OF_YEAR
            "Month" -> Calendar.MONTH
            "Year" -> Calendar.YEAR
            else -> Calendar.DATE // Default to day
        }

        selectedDate.add(field, amount)
        updateDateTextAndColor(selectedDate)
        loadTasksForSelectedDate()
        updateTaskCountViews()
    }

    private fun Calendar.isSameDay(other: Calendar): Boolean {
        return get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }

    private fun openKeyboardFor(editText: EditText) {
        editText.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupAddTodoButton(view: View) {
        view.findViewById<ExtendedFloatingActionButton>(R.id.fabAddTodo).setOnClickListener {
            val taskCard = createTaskCard()

            // Adjusted logic for setting 'addedDate' based on the current filter
            val addedDate = when (currentFilter) {
                "Day" -> selectedDate.time // Use the selected date for "Day"
                "Week" -> selectedDate.time // Use the selected date for "Week", assuming tasks in a week can be added to any day within the week
                "Month" -> {
                    // For Month, set 'addedDate' to the current date within the selected month
                    Calendar.getInstance().apply {
                        time = selectedDate.time
                        set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
                        set(Calendar.MONTH, selectedDate.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH))
                    }.time
                }
                "Year" -> {
                    // For Year, set 'addedDate' to the current date within the selected year
                    Calendar.getInstance().apply {
                        time = selectedDate.time
                        set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
                        set(Calendar.DAY_OF_YEAR, selectedDate.get(Calendar.DAY_OF_YEAR))
                    }.time
                }
                else -> selectedDate.time // Default to using 'selectedDate'
            }

            val newTask = Task(
                uuid = UUID.randomUUID(),
                name = "", // Task name will be set later by the user
                addedDate = addedDate, // Use the adjusted 'addedDate'
                completedDates = mutableListOf(),
                frequency = Frequency.ONCE,
                timeFrame = when (currentFilter) {
                    "Day" -> TimeFrame.DAY
                    "Week" -> TimeFrame.WEEK
                    "Month" -> TimeFrame.MONTH
                    "Year" -> TimeFrame.YEAR
                    else -> TimeFrame.DAY
                },
                tag = null,
                isCompleted = false,
                orderNumber = tasksContainer.childCount + 1
            )

            isNewTaskMap[newTask.uuid] = true

            tasksContainer.addView(taskCard)
            openKeyboardFor(taskCard.findViewById(R.id.etTodoName))
            handleTaskInteractions(taskCard, newTask)
        }
    }

    private fun handleTaskInteractions(cardView: View, task: Task) {
        val taskNameEditText = cardView.findViewById<EditText>(R.id.etTodoName)
        val checkBox = cardView.findViewById<CheckBox>(R.id.cbTodo)

        updateTaskAppearance(task.isCompleted, taskNameEditText)

        taskNameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                focusedTask = task
            } else {
                val newName = taskNameEditText.text.toString().trim()
                if (task.name != newName && newName.isNotEmpty()) {
                    task.name = newName
                    saveTaskAsync(task)
                    focusedTask = null
                }
            }
        }

        checkBox.isChecked = task.isCompleted
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            task.isCompleted = isChecked
            saveTaskAsync(task)
            updateTaskAppearance(isChecked, taskNameEditText)
        }

        cardView.findViewById<ImageView>(R.id.ivTodoMenu).setOnClickListener {
            showPopupMenu(it, task)
        }
    }

    override fun onPause() {
        super.onPause()
        focusedTask?.let {
            saveTaskAsync(it)
            focusedTask = null
        }
    }

    private fun updateTaskAppearance(isCompleted: Boolean, taskNameEditText: EditText) {
        if (isCompleted) {
            taskNameEditText.paintFlags = taskNameEditText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            // Use a theme attribute or color resource that adapts to the theme
            val textColorSecondary = TypedValue().also {
                requireContext().theme.resolveAttribute(android.R.attr.textColorSecondary, it, true)
            }.resourceId
            taskNameEditText.setTextColor(ContextCompat.getColor(requireContext(), textColorSecondary))
        } else {
            taskNameEditText.paintFlags = taskNameEditText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            // Use the primary text color from the theme
            val textColorPrimary = TypedValue().also {
                requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, it, true)
            }.resourceId
            taskNameEditText.setTextColor(ContextCompat.getColor(requireContext(), textColorPrimary))
        }
    }

    private fun saveTaskAsync(task: Task) {
        val taskDao = TaskDatabase.getInstance(requireContext()).taskDao()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val isTaskNew = isNewTaskMap[task.uuid] ?: true // Assume new if not found in map

                if (isTaskNew) {
                    val id = taskDao.insertTask(task)
                } else {
                    taskDao.updateTask(task)
                }

                withContext(Dispatchers.Main) {
                    isNewTaskMap[task.uuid] = false
                    updateTaskCountViews()
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun displayTasks(tasks: List<Task>) {
        tasksContainer.removeAllViews()
        tasks.forEachIndexed { index, task ->
            // Inflate and configure the task card view
            val cardView = createTaskCard().apply {
                findViewById<TextView>(R.id.tvTodoNumber).text = (index + 1).toString()
                findViewById<EditText>(R.id.etTodoName).setText(task.name)

                // Set the checkbox state based on the task's completion status
                val checkBox = findViewById<CheckBox>(R.id.cbTodo)
                checkBox.isChecked = task.isCompleted

                // Mark the task as not new since it's being loaded from the database
                isNewTaskMap[task.uuid] = false

                // Handle interactions for this task card
                handleTaskInteractions(this, task)
            }

            // Add the configured task card to the container
            tasksContainer.addView(cardView)
        }
        updateTaskCountViews()
    }

    private fun loadTasksForSelectedDate() {

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allTasks = TaskDatabase.getInstance(requireContext()).taskDao().getAllTasks()
                val (startDate, endDate) = getDateRangeForFilter()
                Log.d("TaskLoad", "Loading tasks for $currentFilter: StartDate=${startDate}, EndDate=${endDate}")

                val filteredTasks = allTasks.filter { task ->
                    val taskInTimeFrame = task.addedDate in startDate..endDate
                    val taskMatchesTimeFrame = when (currentFilter) {
                        "Day" -> task.timeFrame == TimeFrame.DAY
                        "Week" -> task.timeFrame == TimeFrame.WEEK
                        "Month" -> task.timeFrame == TimeFrame.MONTH
                        "Year" -> task.timeFrame == TimeFrame.YEAR
                        else -> false
                    }
                    taskInTimeFrame && taskMatchesTimeFrame
                }

                Log.d("TaskLoad", "Loaded ${filteredTasks.size} tasks for $currentFilter. TaskInTimeFrame & TaskMatchesTimeFrame applied.")

                withContext(Dispatchers.Main) {
                    displayTasks(filteredTasks)
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("TaskLoad", "Error loading tasks: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load tasks.", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun DatePair.containsDate(date: Date): Boolean {
        return date in startDate..endDate
    }

    private fun showPopupMenu(view: View, task: Task) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.todo_option_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete -> {
                    confirmAndDeleteTask(task)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmAndDeleteTask(task: Task) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteTask(task)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun deleteTask(task: Task) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                TaskDatabase.getInstance(requireContext()).taskDao().deleteTask(task)

                withContext(Dispatchers.Main) {
                    loadTasksForSelectedDate()
                }
            } catch (e: Exception) {
                // Handle exception, e.g., show an error message
            }
        }
    }
    
}