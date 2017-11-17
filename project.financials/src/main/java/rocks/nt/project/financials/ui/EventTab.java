package rocks.nt.project.financials.ui;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.vaadin.data.HasValue.ValueChangeListener;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.ErrorMessage;
import com.vaadin.server.Page;
import com.vaadin.server.UserError;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBoxGroup;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.DateField;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import rocks.nt.project.financials.data.ProjectAssignment;
import rocks.nt.project.financials.data.ProjectAssignment.EventBuilder;
import rocks.nt.project.financials.services.InfluxService;
import rocks.nt.project.financials.services.PropertiesService;

/**
 * UI Tab element for event management.
 * 
 * @author awe
 *
 */
public class EventTab {
	/**
	 * Vacation constant string.
	 */
	private static final String VACATION = "VACATION";

	/**
	 * Training constant string.
	 */
	private static final String TRAINING = "TRAINING";

	/**
	 * Conference constant string.
	 */
	private static final String CONFERENCE = "CONFERENCE";

	/**
	 * Other events constant string.
	 */
	private static final String OTHER = "OTHER";

	/**
	 * Employee selection all except.
	 */
	private static final String ALL_EXCEPT_EMPLOYEES = "ALL EXCEPT";

	/**
	 * Employee selection, selected set.
	 */
	private static final String SOME_EMPLOYEES = "SELECTED";

	/**
	 * Assign button.
	 */
	private Button assignButton;

	/**
	 * From date field.
	 */
	private DateField fromDateField;

	/**
	 * To date field.
	 */
	private DateField toDateField;

	/**
	 * Event selection combo box.
	 */
	private ComboBox<String> eventComboBox;

	/**
	 * Employee selection check box group.
	 */
	private CheckBoxGroup<String> employeesExcludeGroup;

	/**
	 * Reload page checkbox.
	 */
	private CheckBoxGroup<String> reloadPageCheckBox;

	/**
	 * Notes text field.
	 */
	private TextField notesField;

	/**
	 * Employee selection combo box.
	 */
	private ComboBox<String> employeeComboBox;

	/**
	 * Constructor.
	 * 
	 * @param tabSheet
	 *            Tab sheet to add this tab to.
	 */
	public EventTab(TabSheet tabSheet) {
		final VerticalLayout projectsTab = new VerticalLayout();
		projectsTab.setMargin(true);
		projectsTab.setSpacing(true);

		createProjectsTab(projectsTab);
		tabSheet.addTab(projectsTab, "Manage Event/Vacation");
	}

	/**
	 * Creates UI elements.
	 * 
	 * @param projectsTab
	 *            root layout
	 */
	private void createProjectsTab(VerticalLayout projectsTab) {
		// Form Layout
		FormLayout formLayout = new FormLayout();
		formLayout.setSizeFull();

		// Project Selection
		eventComboBox = new ComboBox<String>("Event");
		eventComboBox.setTextInputAllowed(false);
		final Set<String> eventSelection = new HashSet<String>();
		eventSelection.add(VACATION);
		eventSelection.add(TRAINING);
		eventSelection.add(CONFERENCE);
		eventSelection.add(OTHER);
		eventComboBox.setItems(eventSelection);
		eventComboBox.setSelectedItem(VACATION);
		eventComboBox.setIcon(VaadinIcons.ASTERISK);
		eventComboBox.setEmptySelectionAllowed(false);

		// Time range selection
		fromDateField = new DateField();
		toDateField = new DateField();

		final UserError fromError = new UserError("'From' date must not be after 'To' date!");
		final UserError toError = new UserError("'To' date must not be before 'From' date!");

		ValueChangeListener<LocalDate> valueChangeListener = event -> {
			if (fromDateField.getValue().isAfter(toDateField.getValue())) {
				toDateField.setComponentError(toError);
				fromDateField.setComponentError(fromError);
			} else {
				toDateField.setComponentError(null);
				fromDateField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		};
		final String dateFormat = "yyyy-MM-dd";
		fromDateField.setCaption("From");
		fromDateField.setDateFormat(dateFormat);
		fromDateField.setValue(LocalDate.now());
		fromDateField.addValueChangeListener(event -> {
			if (fromDateField.getValue().isAfter(toDateField.getValue())) {
				toDateField.setValue(fromDateField.getValue());
			}
			toDateField.setComponentError(null);
			fromDateField.setComponentError(null);
		});

		toDateField.setCaption("To");
		toDateField.setDateFormat(dateFormat);
		toDateField.setValue(LocalDate.now().plusDays(1));
		toDateField.addValueChangeListener(valueChangeListener);

		// Employee Selection
		employeeComboBox = new ComboBox<String>("Employee");
		final List<String> employees = InfluxService.getInstance().getKnownEmployees();
		final LinkedList<String> employeeSelection = new LinkedList<String>();
		employeeSelection.addAll(employees);
		employeeSelection.addFirst(SOME_EMPLOYEES);
		employeeSelection.addFirst(ALL_EXCEPT_EMPLOYEES);

		employeeComboBox.setItems(employeeSelection);
		employeeComboBox.setIcon(VaadinIcons.USER);
		employeeComboBox.setSelectedItem(ALL_EXCEPT_EMPLOYEES);
		employeeComboBox.setNewItemHandler(inputString -> {
			String trimmedString = inputString.trim();
			if (!trimmedString.isEmpty()) {
				employeeSelection.add(trimmedString);
				employeeComboBox.setItems(employeeSelection);
				employeeComboBox.setSelectedItem(trimmedString);
			}
		});
		employeeComboBox.setEmptySelectionAllowed(false);
		employeeComboBox.addSelectionListener(event -> {
			if (ALL_EXCEPT_EMPLOYEES.equals(employeeComboBox.getSelectedItem().get())) {
				employeesExcludeGroup.setVisible(true);
				employeesExcludeGroup.setCaption("Employees to exclude:");
			} else if (SOME_EMPLOYEES.equals(employeeComboBox.getSelectedItem().get())) {
				employeesExcludeGroup.setVisible(true);
				employeesExcludeGroup.setCaption("Select affected employees:");
			} else {
				employeesExcludeGroup.setVisible(false);
			}
		});

		// EmployeesToExclude
		employeesExcludeGroup = new CheckBoxGroup<String>("Employees to exclude:", employees);
		employeesExcludeGroup.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);
		employeesExcludeGroup.addStyleName(ValoTheme.CHECKBOX_SMALL);

		// Assign Button
		assignButton = new Button("Create / Update Event");
		assignButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
		assignButton.addClickListener(event -> CompletableFuture.runAsync(this::assignEvent));

		// Reload Page Checkbox
		Set<String> reloadCheckItem = new HashSet<String>();
		reloadCheckItem.add("Reload Page");
		reloadPageCheckBox = new CheckBoxGroup<String>("", reloadCheckItem);

		// Notes Field
		notesField = new TextField("Notes");
		notesField.setSizeFull();

		// Row layouts
		HorizontalLayout row1 = new HorizontalLayout(employeeComboBox, eventComboBox, fromDateField, toDateField);
		HorizontalLayout row2 = new HorizontalLayout(employeesExcludeGroup);
		HorizontalLayout row3 = new HorizontalLayout(notesField, reloadPageCheckBox, assignButton);
		row3.setComponentAlignment(assignButton, Alignment.BOTTOM_LEFT);

		formLayout.addComponent(row1);
		formLayout.addComponent(row2);
		formLayout.addComponent(row3);

		projectsTab.addComponent(formLayout);
		projectsTab.setComponentAlignment(formLayout, Alignment.TOP_CENTER);
	}

	/**
	 * Sets enabled state for assign button.
	 */
	private void setEnabledStateForAssignButton() {
		ErrorMessage fromDateError = fromDateField.getErrorMessage();
		ErrorMessage toDateError = toDateField.getErrorMessage();
		if (null == fromDateError && null == toDateError) {
			assignButton.setEnabled(true);
		} else {
			assignButton.setEnabled(false);
		}
	}

	/**
	 * Assigns event based on the input data.
	 */
	private void assignEvent() {
		String event = eventComboBox.getValue();
		String color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_DEFAULT_KEY);
		if (event.equals(VACATION)) {
			event = PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY);
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_NA_KEY);
		} else if (event.equals(TRAINING)) {
			event = PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_TRAINING_KEY);
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_TRAINING_KEY);
		} else if (event.equals(CONFERENCE)) {
			event = PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_CONFERENCE_KEY);
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_CONFERENCE_KEY);
		} else if (event.equals(OTHER)) {
			event = PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_OTHER_KEY);
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_OTHER_KEY);
		}
		Set<String> employees = getAffectedEmployees();
		ProjectAssignment[] projectAssignments = new ProjectAssignment[employees.size()];
		int i = 0;
		for (String employee : employees) {
			EventBuilder builder = new EventBuilder();
			builder.employee(employee).event(event).from(fromDateField.getValue()).to(toDateField.getValue())
					.color(color).notes(notesField.getValue().trim());
			projectAssignments[i] = builder.build();
			i++;
		}
		InfluxService.getInstance().assignProjects(projectAssignments);

		if (!reloadPageCheckBox.getValue().isEmpty()) {
			Page.getCurrent().reload();
		}
	}

	/**
	 * Calculate affected employees.
	 * 
	 * @return set of affected employees
	 */
	private Set<String> getAffectedEmployees() {
		Set<String> resultSet = new HashSet<String>();
		if (ALL_EXCEPT_EMPLOYEES.equals(employeeComboBox.getValue())) {
			resultSet.addAll(InfluxService.getInstance().getKnownEmployees());
			resultSet.removeAll(employeesExcludeGroup.getValue());
		} else if (SOME_EMPLOYEES.equals(employeeComboBox.getValue())) {
			resultSet.addAll(employeesExcludeGroup.getValue());
		} else {
			resultSet.add(employeeComboBox.getValue());
		}
		return resultSet;
	}
}
