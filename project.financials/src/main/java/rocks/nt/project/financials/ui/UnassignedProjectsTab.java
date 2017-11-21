package rocks.nt.project.financials.ui;

import java.time.LocalDate;
import java.util.HashSet;
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

import rocks.nt.project.financials.services.InfluxService;
import rocks.nt.project.financials.services.PropertiesService;

public class UnassignedProjectsTab {

	/**
	 * Assign button.
	 */
	private Button createButton;

	/**
	 * Delete button.
	 */
	private Button deleteButton;

	/**
	 * From date field.
	 */
	private DateField fromDateField;

	/**
	 * To date field.
	 */
	private DateField toDateField;

	/**
	 * Project selection combo box.
	 */
	private ComboBox<String> projectComboBox;

	/**
	 * Reload page checkbox.
	 */
	private CheckBoxGroup<String> reloadPageCheckBox;

	/**
	 * notest text field.
	 */
	private TextField notesField;

	/**
	 * Projects that are in the database.
	 */
	private final Set<String> knownProjects = new HashSet<String>();

	/**
	 * Constructor.
	 * 
	 * @param tabSheet
	 *            tab sheet to add this tab to.
	 */
	public UnassignedProjectsTab(TabSheet tabSheet) {
		final VerticalLayout projectsTab = new VerticalLayout();
		projectsTab.setMargin(true);
		projectsTab.setSpacing(true);

		createProjectsTab(projectsTab);
		tabSheet.addTab(projectsTab, "Unassigned Projects");
	}

	/**
	 * Create UI elements.
	 * 
	 * @param projectsTab
	 *            root layout.
	 */
	private void createProjectsTab(VerticalLayout projectsTab) {
		// Form Layout
		FormLayout formLayout = new FormLayout();
		formLayout.setSizeFull();

		// Project Selection
		projectComboBox = new ComboBox<String>("Project");
		final List<String> projectSelection = InfluxService.getInstance().getKnownUnassignedProjects();
		knownProjects.addAll(projectSelection);
		projectComboBox.setItems(projectSelection);
		projectComboBox.setIcon(VaadinIcons.TOOLBOX);
		if (!projectSelection.isEmpty()) {
			projectComboBox.setSelectedItem(projectSelection.get(0));
		} else {
			String defaultValue = "Project";
			projectSelection.add(defaultValue);
			projectComboBox.setItems(projectSelection);
			projectComboBox.setSelectedItem(defaultValue);
		}

		projectComboBox.setNewItemHandler(inputString -> {
			String trimmedString = inputString.trim();
			if (!trimmedString.isEmpty() && !projectSelection.contains(trimmedString)) {
				projectSelection.add(trimmedString);
				projectComboBox.setItems(projectSelection);
			}
			projectComboBox.setSelectedItem(trimmedString);
		});
		projectComboBox.setEmptySelectionAllowed(false);

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

		// Assign Button
		createButton = new Button("Assign Project");
		createButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
		createButton.addClickListener(event -> this.createUnassignedProject());

		// Assign Button
		deleteButton = new Button("Delete");
		deleteButton.addStyleName(ValoTheme.BUTTON_DANGER);
		deleteButton.addClickListener(event -> this.deleteUnassignedProject());
		projectComboBox.addSelectionListener(e -> {
			setEnabledStateForDeleteButton();
		});

		// Reload Page Checkbox
		Set<String> reloadCheckItem = new HashSet<String>();
		reloadCheckItem.add("Reload Page");
		reloadPageCheckBox = new CheckBoxGroup<String>("", reloadCheckItem);
		reloadPageCheckBox.setValue(reloadCheckItem);
		// Notes Field
		notesField = new TextField("Notes");
		notesField.setSizeFull();

		HorizontalLayout row1 = new HorizontalLayout(projectComboBox, fromDateField, toDateField);

		HorizontalLayout row2 = new HorizontalLayout(notesField, reloadPageCheckBox, deleteButton, createButton);
		row2.setComponentAlignment(createButton, Alignment.BOTTOM_LEFT);
		row2.setComponentAlignment(deleteButton, Alignment.BOTTOM_LEFT);
		// row2.setHeight("62px");

		formLayout.addComponent(row1);
		formLayout.addComponent(row2);

		projectsTab.addComponent(formLayout);
		projectsTab.setComponentAlignment(formLayout, Alignment.TOP_CENTER);
		setEnabledStateForDeleteButton();
	}

	/**
	 * Set enabled state for button.
	 */
	private void setEnabledStateForAssignButton() {
		ErrorMessage fromDateError = fromDateField.getErrorMessage();
		ErrorMessage toDateError = toDateField.getErrorMessage();
		if (null == fromDateError && null == toDateError) {
			createButton.setEnabled(true);
		} else {
			createButton.setEnabled(false);
		}
	}

	/**
	 * Creates unassigned project based on input data in the form.
	 */
	private void createUnassignedProject() {
		String project = projectComboBox.getValue();
		String color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_UNASSIGNED_KEY);

		InfluxService.getInstance().createUnassignedProject(project, fromDateField.getValue(), toDateField.getValue(),
				notesField.getValue().trim(), color);

		if (!reloadPageCheckBox.getValue().isEmpty()) {
			Page.getCurrent().getJavaScript().execute("var grafanaFrame = document.getElementsByClassName(\"v-browserframe\")[0].children[0]; grafanaFrame.src = grafanaFrame.src;");
		} else {
			knownProjects.clear();
			knownProjects.addAll(InfluxService.getInstance().getKnownUnassignedProjects());
			setEnabledStateForDeleteButton();
		}
	}

	/**
	 * 
	 */
	private void setEnabledStateForDeleteButton() {
		if (knownProjects.contains(projectComboBox.getValue())) {
			deleteButton.setEnabled(true);
		} else {
			deleteButton.setEnabled(false);
		}
	}

	/**
	 * Deletes unassigned project based on input data in the form.
	 */
	private void deleteUnassignedProject() {
		String project = projectComboBox.getValue();

		InfluxService.getInstance().deleteUnassignedProject(project, fromDateField.getValue(), toDateField.getValue(),
				null);

		if (!reloadPageCheckBox.getValue().isEmpty()) {
			Page.getCurrent().getJavaScript().execute("var grafanaFrame = document.getElementsByClassName(\"v-browserframe\")[0].children[0]; grafanaFrame.src = grafanaFrame.src;");
		}
	}

}
