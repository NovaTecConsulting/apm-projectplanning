package rocks.nt.project.financials.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.w3c.dom.DOMConfiguration;

import com.vaadin.data.HasValue.ValueChangeListener;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.ErrorMessage;
import com.vaadin.server.Page;
import com.vaadin.server.UserError;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.CheckBoxGroup;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.DateField;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.JavaScript;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import rocks.nt.project.financials.data.ProjectAssignment.ProjectAssignmentBuilder;
import rocks.nt.project.financials.services.InfluxService;
import rocks.nt.project.financials.services.PropertiesService;

/**
 * UI Tab element for project planning.
 * 
 * @author awe
 *
 */
public class ProjectsTab {
	/**
	 * Project deletion constant string.
	 */
	private static final String NO_PROJECT = "NO PROJECT";

	/**
	 * Booking states.
	 */
	private static final String[] BOOKING_STATES = {
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_REQUEST_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_SOFT_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_HARD_KEY) };

	/**
	 * Assign button
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
	 * Field for daily rate.
	 */
	private TextField rateField;

	/**
	 * Employee selection combo box.
	 */
	private ComboBox<String> employeeComboBox;

	/**
	 * Project selection combo box.
	 */
	private ComboBox<String> projectComboBox;

	/**
	 * Booking status selection combo box.
	 */
	private ComboBox<String> bookingStatusComboBox;

	/**
	 * Selection of days of the week for project assignment.
	 */
	private CheckBoxGroup<DayOfWeek> checkBoxGroup;

	/**
	 * Skip public holidays check box.
	 */
	private CheckBox skipPublicHolidaysCheckBox;

	/**
	 * Skip other event check box.
	 */
	private CheckBox skipOtherEventsCheckBox;

	/**
	 * Reload page check box.
	 */
	private CheckBox reloadPageCheckBox;

	/**
	 * Text field for notes.
	 */
	private TextField notesField;

	/**
	 * Expected expenses field.
	 */
	private TextField expectedExpensesField;

	/**
	 * Constructor.
	 * 
	 * @param tabSheet
	 *            tab sheet where to add this tab.
	 */
	public ProjectsTab(TabSheet tabSheet) {
		final VerticalLayout projectsTab = new VerticalLayout();
		projectsTab.setMargin(true);
		projectsTab.setSpacing(true);

		createProjectsTab(projectsTab);
		tabSheet.addTab(projectsTab, "Add/Edit Project Assignment");
	}

	/**
	 * Create UI elements
	 * 
	 * @param projectsTab
	 *            root layout
	 */
	private void createProjectsTab(VerticalLayout projectsTab) {
		// Form Layout
		FormLayout formLayout = new FormLayout();
		formLayout.setSizeFull();

		// Employee Selection
		employeeComboBox = new ComboBox<String>("Employee");
		List<String> employeeSelection = InfluxService.getInstance().getKnownEmployees();

		employeeComboBox.setItems(employeeSelection);
		employeeComboBox.setIcon(VaadinIcons.USER);
		if (!employeeSelection.isEmpty()) {
			employeeComboBox.setSelectedItem(employeeSelection.get(0));
		} else {
			String defaultValue = "Mustermann";
			employeeSelection.add(defaultValue);
			employeeComboBox.setItems(employeeSelection);
			employeeComboBox.setSelectedItem(defaultValue);
		}

		employeeComboBox.setNewItemHandler(inputString -> {
			String trimmedString = inputString.trim();
			if (!trimmedString.isEmpty() && !employeeSelection.contains(trimmedString)) {
				employeeSelection.add(trimmedString);
				employeeComboBox.setItems(employeeSelection);
				employeeComboBox.setSelectedItem(trimmedString);
			}
		});
		employeeComboBox.setEmptySelectionAllowed(false);

		// Project Selection
		projectComboBox = new ComboBox<String>("Project");
		final List<String> projectSelection = InfluxService.getInstance().getKnownProjects();
		projectSelection.add(0, NO_PROJECT);

		projectComboBox.setItems(projectSelection);
		projectComboBox.setIcon(VaadinIcons.TOOLBOX);
		projectComboBox.setSelectedItem(projectSelection.get(0));

		projectComboBox.addSelectionListener(e -> {
			if (projectComboBox.getValue().equals(NO_PROJECT)) {
				setEnabledStateForCustomerProjectFields(false);
			} else {
				setEnabledStateForCustomerProjectFields(true);
			}
		});

		projectComboBox.setNewItemHandler(inputString -> {
			String trimmedString = inputString.trim();
			if (!trimmedString.isEmpty() && !projectSelection.contains(trimmedString)) {
				projectSelection.add(trimmedString);
				projectComboBox.setItems(projectSelection);
				projectComboBox.setSelectedItem(trimmedString);
			}
		});
		projectComboBox.setEmptySelectionAllowed(false);

		// Booking status
		bookingStatusComboBox = new ComboBox<String>("Booking Status", Arrays.asList(BOOKING_STATES));
		bookingStatusComboBox.setValue(
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_REQUEST_KEY));
		bookingStatusComboBox.setEmptySelectionAllowed(false);
		bookingStatusComboBox.setTextInputAllowed(false);
		bookingStatusComboBox.setIcon(VaadinIcons.CLIPBOARD_CHECK);
		bookingStatusComboBox.setItemIconGenerator(item -> {
			if (item.equals(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_REQUEST_KEY))) {
				return VaadinIcons.QUESTION_CIRCLE_O;
			} else if (item.equals(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_SOFT_KEY))) {
				return VaadinIcons.QUESTION_CIRCLE;
			} else if (item.equals(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_HARD_KEY))) {
				return VaadinIcons.EXCLAMATION_CIRCLE;
			}
			return null;
		});
		// Rate
		rateField = new TextField("Daily Rate");
		rateField.setPlaceholder("€1000");
		rateField.setValue("€1000");
		rateField.addValueChangeListener(event -> {
			if (!event.getValue().matches("[€]?[\\s]*[0-9]+")) {
				rateField.setComponentError(new UserError("This is not a valid daily rate format!"));
			} else {
				rateField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		});
		rateField.setIcon(VaadinIcons.EURO);

		// Expected Expenses
		expectedExpensesField = new TextField("Expected Daily Expenses");
		expectedExpensesField.setPlaceholder("€200");
		expectedExpensesField.setValue("€0");
		expectedExpensesField.addValueChangeListener(event -> {
			if (!event.getValue().matches("[€]?[\\s]*[0-9]+")) {
				expectedExpensesField.setComponentError(new UserError("This is not a valid expenses format!"));
			} else {
				expectedExpensesField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		});
		expectedExpensesField.setIcon(VaadinIcons.AIRPLANE);

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

		// Days of Week selection
		checkBoxGroup = new CheckBoxGroup<DayOfWeek>("Days", Arrays.asList(DayOfWeek.values()));
		checkBoxGroup.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);
		checkBoxGroup.setItemCaptionGenerator(item -> item.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));

		Set<DayOfWeek> selectedDays = new HashSet<DayOfWeek>();
		selectedDays.add(DayOfWeek.MONDAY);
		selectedDays.add(DayOfWeek.TUESDAY);
		selectedDays.add(DayOfWeek.WEDNESDAY);
		selectedDays.add(DayOfWeek.THURSDAY);
		checkBoxGroup.setValue(selectedDays);
		checkBoxGroup.addStyleName(ValoTheme.CHECKBOX_SMALL);

		// Skip Public Holidays
		skipPublicHolidaysCheckBox = new CheckBox("Skip Public Holidays     ");
		skipPublicHolidaysCheckBox.addStyleName(ValoTheme.CHECKBOX_SMALL);
		skipPublicHolidaysCheckBox.setValue(true);

		// Skip Events
		skipOtherEventsCheckBox = new CheckBox("Skip Other Events");
		skipOtherEventsCheckBox.setValue(true);
		skipOtherEventsCheckBox.addStyleName(ValoTheme.CHECKBOX_SMALL);

		CheckBox cb1 = new CheckBox("Test1");
		cb1.addStyleName(ValoTheme.CHECKBOX_SMALL);
		CheckBox cb2 = new CheckBox("Test2");
		cb2.addStyleName(ValoTheme.CHECKBOX_SMALL);
		VerticalLayout vl = new VerticalLayout(cb1, cb2);
		vl.setMargin(false);
		// Assign Button
		assignButton = new Button("Assign Project");
		assignButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
		assignButton.addClickListener(event -> this.assignProject());

		// Reload Page Checkbox
		reloadPageCheckBox = new CheckBox("Reload Page");
		reloadPageCheckBox.setValue(true);
		reloadPageCheckBox.addStyleName(ValoTheme.CHECKBOX_SMALL);

		// Notes Field
		notesField = new TextField("Notes");
		notesField.setSizeFull();

		GridLayout gridLayout = new GridLayout(2, 2);
		gridLayout.addComponent(skipPublicHolidaysCheckBox, 0, 0);
		gridLayout.addComponent(skipOtherEventsCheckBox, 1, 0);
		gridLayout.addComponent(reloadPageCheckBox, 0, 1);
		gridLayout.addComponent(assignButton, 1, 1);
		gridLayout.setMargin(false);
		gridLayout.setSpacing(true);

		HorizontalLayout row1 = new HorizontalLayout(employeeComboBox, projectComboBox, fromDateField, toDateField);
		HorizontalLayout row2 = new HorizontalLayout(rateField, expectedExpensesField, checkBoxGroup);
		HorizontalLayout row3 = new HorizontalLayout(bookingStatusComboBox, notesField, gridLayout);

		formLayout.addComponent(row1);
		formLayout.addComponent(row2);
		formLayout.addComponent(row3);

		projectsTab.addComponent(formLayout);
		projectsTab.setComponentAlignment(formLayout, Alignment.TOP_CENTER);
		setEnabledStateForCustomerProjectFields(false);
	}

	private void setEnabledStateForAssignButton() {
		ErrorMessage rateError = rateField.getErrorMessage();
		ErrorMessage fromDateError = fromDateField.getErrorMessage();
		ErrorMessage toDateError = toDateField.getErrorMessage();
		if (null == rateError && null == fromDateError && null == toDateError) {
			assignButton.setEnabled(true);
		} else {
			assignButton.setEnabled(false);
		}
	}

	/**
	 * Assign project based on input fields.
	 */
	private void assignProject() {


		double dailyRate = getDailyRate();
		double expenses = getExpenses();
		String project = projectComboBox.getValue();
		String bookingStatus = bookingStatusComboBox.getValue();
		String color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_DEFAULT_KEY);
		String notes = notesField.getValue().trim();
		if (expenses > 0.0) {
			if (!notes.isEmpty()) {
				notes += " - ";
			}
			notes += "Expected Daily Expenses: €" + String.valueOf(expenses);
		}
		if (project.equals(NO_PROJECT)) {
			project = PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY);
			dailyRate = 0.0;
			expenses = 0.0;
			bookingStatus = PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY);
			notes = "";
		} else if (bookingStatus.equals(
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_REQUEST_KEY))) {
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_STATUS_REQUEST_KEY);
		} else if (bookingStatus.equals(
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_SOFT_KEY))) {
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_STATUS_SOFT_KEY);
		} else if (bookingStatus.equals(
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_BOOKING_STATUS_HARD_KEY))) {
			color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_STATUS_HARD_KEY);
		}

		ProjectAssignmentBuilder builder = new ProjectAssignmentBuilder();
		builder.employee(employeeComboBox.getValue()).project(project).status(bookingStatus).rate(dailyRate)
				.from(fromDateField.getValue()).to(toDateField.getValue()).daysOfWeek(checkBoxGroup.getValue())
				.skipHolidays(skipPublicHolidaysCheckBox.getValue()).skipEvents(skipOtherEventsCheckBox.getValue())
				.color(color).notes(notes).expenses(expenses);
		InfluxService.getInstance().assignProjects(builder.build());
		

		
		CompletableFuture.runAsync(new  Runnable() {
			
			@Override
			public void run() {
				InfluxService.getInstance().createWeekEndsAndPublicHolidays(employeeComboBox.getValue(),
						fromDateField.getValue(), toDateField.getValue());
			}});
		

		
		if (reloadPageCheckBox.getValue()) {
			Page.getCurrent().getJavaScript().execute("var grafanaFrame = document.getElementsByClassName(\"v-browserframe\")[0].children[0]; grafanaFrame.src = grafanaFrame.src;");
		}

		
	}

	/**
	 * Retrieve daily rate value.
	 * 
	 * @return daily rate
	 */
	private double getDailyRate() {
		String rateString = rateField.getValue();
		if (rateString.startsWith("€")) {
			rateString = rateString.substring(1);
		}
		return Double.parseDouble(rateString.trim());
	}

	/**
	 * Retrieve daily expenses value.
	 * 
	 * @return daily expenses
	 */
	private double getExpenses() {
		String expensesString = expectedExpensesField.getValue();
		if (expensesString.startsWith("€")) {
			expensesString = expensesString.substring(1);
		}
		return Double.parseDouble(expensesString.trim());
	}

	/**
	 * Sets the enabled state.
	 */
	private void setEnabledStateForCustomerProjectFields(boolean enabled) {
		rateField.setEnabled(enabled);
		expectedExpensesField.setEnabled(enabled);
		bookingStatusComboBox.setEnabled(enabled);
		notesField.setEnabled(enabled);
	}

}
