package rocks.nt.project.financials.ui;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.ErrorMessage;
import com.vaadin.server.UserError;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.ValoTheme;

import rocks.nt.project.financials.data.MonthReportDataPoint;
import rocks.nt.project.financials.data.MonthReportDataPoint.Type;
import rocks.nt.project.financials.services.InfluxService;

/**
 * Dialog for entering monthly report data.
 * 
 * @author awe
 *
 */
public class RetrospectDataWindow extends Window {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6340844764797664653L;

	private static final String WINDOW_WIDTH = "420px";

	private static final String EURO_PATTERN = "[€]?[\\\\s]*[0-9]+";
	private static final String PERCENTAGE_PATTERN = "0*1?[0-9]?[0-9]%?";
	/**
	 * Field for daily rate.
	 */
	private TextField revenueField;

	private TextField expensesField;

	private TextField overallCostsField;

	private TextField utilizationField;

	private ComboBox<Month> monthPicker;
	private ComboBox<String> yearPicker;

	/**
	 * Enter button
	 */
	private Button enterButton;

	public RetrospectDataWindow() {
		super("Enter actual month report data"); // Set window caption
		center();

		setWidth(WINDOW_WIDTH);
		setClosable(true);
		createContent();
	}

	/**
	 * Creates dialog content.
	 */
	private void createContent() {
		// Form Layout
		FormLayout formLayout = new FormLayout();
		// formLayout.setSizeFull();

		monthPicker = new ComboBox<Month>("Month");
		monthPicker.setTextInputAllowed(false);

		monthPicker.setItems(Month.values());
		monthPicker.setSelectedItem(LocalDate.now().minusMonths(1).getMonth());
		monthPicker.setItemCaptionGenerator(item -> item.getDisplayName(TextStyle.FULL, Locale.GERMANY));
		monthPicker.setIcon(VaadinIcons.CALENDAR);
		monthPicker.setEmptySelectionAllowed(false);

		yearPicker = new ComboBox<String>("Year");
		yearPicker.setTextInputAllowed(false);
		List<String> years = new ArrayList<>();
		for (LocalDate tmp = LocalDate.now().minusYears(10); tmp
				.isBefore(LocalDate.now().plusYears(11)); tmp = tmp.plusYears(1)) {
			years.add(String.valueOf(tmp.getYear()));
		}

		yearPicker.setItems(years);
		yearPicker.setSelectedItem(String.valueOf(LocalDate.now().getYear()));
		yearPicker.setIcon(VaadinIcons.CALENDAR_O);
		yearPicker.setEmptySelectionAllowed(false);

		// Revenue
		revenueField = new TextField("Revenue");
		revenueField.setPlaceholder("€1000");
		revenueField.setValue("€0");
		revenueField.addValueChangeListener(event -> {
			if (!event.getValue().matches(EURO_PATTERN)) {
				revenueField.setComponentError(new UserError("This is not a valid daily rate format!"));
			} else {
				revenueField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		});
		revenueField.setIcon(VaadinIcons.EURO);

		// Expenses
		expensesField = new TextField("Expenses");
		expensesField.setPlaceholder("€1000");
		expensesField.setValue("€0");
		expensesField.addValueChangeListener(event -> {
			if (!event.getValue().matches(EURO_PATTERN)) {
				expensesField.setComponentError(new UserError("This is not a valid daily rate format!"));
			} else {
				expensesField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		});
		expensesField.setIcon(VaadinIcons.AIRPLANE);

		// Overall costs
		overallCostsField = new TextField("All Costs");
		overallCostsField.setPlaceholder("€1000");
		overallCostsField.setValue("€0");
		overallCostsField.addValueChangeListener(event -> {
			if (!event.getValue().matches(EURO_PATTERN)) {
				overallCostsField.setComponentError(new UserError("This is not a valid daily rate format!"));
			} else {
				overallCostsField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		});
		overallCostsField.setIcon(VaadinIcons.MONEY_WITHDRAW);

		// Utilization
		utilizationField = new TextField("Utilization");
		utilizationField.setPlaceholder("80%");
		utilizationField.setValue("0%");
		utilizationField.addValueChangeListener(event -> {
			if (!event.getValue().matches(PERCENTAGE_PATTERN)) {
				utilizationField.setComponentError(new UserError("This is not a valid percentage format!"));
			} else {
				utilizationField.setComponentError(null);
			}
			setEnabledStateForAssignButton();
		});
		utilizationField.setIcon(VaadinIcons.USER_CLOCK);

		// Enter Button
		enterButton = new Button("Submit");
		enterButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
		enterButton.addClickListener(event -> {
			enterData();
		});

		HorizontalLayout row1 = new HorizontalLayout(monthPicker, yearPicker);
		HorizontalLayout row2 = new HorizontalLayout(expensesField, utilizationField);
		HorizontalLayout row3 = new HorizontalLayout(revenueField, overallCostsField);

		formLayout.addComponent(row1);
		formLayout.addComponent(row2);
		formLayout.addComponent(row3);
		formLayout.addComponent(enterButton);
		setContent(formLayout);
	}

	/**
	 * Sets enabled state for button.
	 */
	private void setEnabledStateForAssignButton() {
		ErrorMessage rateError = revenueField.getErrorMessage();
		ErrorMessage expensesError = expensesField.getErrorMessage();
		ErrorMessage utilizationError = utilizationField.getErrorMessage();
		if (null == rateError && null == expensesError && null == utilizationError) {
			enterButton.setEnabled(true);
		} else {
			enterButton.setEnabled(false);
		}
	}

	/**
	 * Submits data.
	 */
	private void enterData() {
		MonthReportDataPoint dataPoint = new MonthReportDataPoint();
		dataPoint.setYearMonth(yearPicker.getValue() + "-" + monthPicker.getValue().getValue());
		dataPoint.setExpenses(getMoneyValue(expensesField));
		dataPoint.setCosts(getMoneyValue(overallCostsField));
		dataPoint.setRevenue(getMoneyValue(revenueField));
		double profit = dataPoint.getRevenue() - dataPoint.getCosts();
		double returnOnSales = dataPoint.getRevenue() == 0.0 ? Double.MIN_VALUE : profit / dataPoint.getRevenue();
		dataPoint.setProfit(profit);
		dataPoint.setUtilization(getPercentageValue(utilizationField));
		dataPoint.setRetrunOnSales(returnOnSales);
		dataPoint.setType(Type.ACTUAL);
		InfluxService.getInstance().enterMonthReportData(dataPoint);
		close();
	}

	/**
	 * Retrieve daily rate value.
	 * 
	 * @return daily rate
	 */
	private double getMoneyValue(TextField field) {
		String valueString = field.getValue();
		if (valueString.startsWith("€")) {
			valueString = valueString.substring(1);
		}
		return Double.parseDouble(valueString.trim());
	}

	/**
	 * Retrieves percentage value from passed text field.
	 * 
	 * @param field
	 *            text field
	 * @return percentage value
	 */
	private double getPercentageValue(TextField field) {
		String valueString = field.getValue();
		if (valueString.endsWith("%")) {
			valueString = valueString.substring(0, valueString.length() - 1);
		}
		return Double.parseDouble(valueString.trim());
	}
}
