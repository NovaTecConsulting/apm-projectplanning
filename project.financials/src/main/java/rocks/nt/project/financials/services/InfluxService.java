package rocks.nt.project.financials.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.ActiveSpan;
import rocks.nt.project.financials.data.Holiday;
import rocks.nt.project.financials.data.MonthReportDataPoint;
import rocks.nt.project.financials.data.MonthReportDataPoint.Type;
import rocks.nt.project.financials.data.ProjectAssignment;
import rocks.nt.project.financials.data.ProjectAssignment.EventBuilder;
import rocks.nt.project.financials.data.ProjectAssignment.ProjectAssignmentBuilder;
import rocks.nt.project.financials.rest.Api;
import rocks.nt.project.financials.rest.ProjectDeleteRequest;

/**
 * Influx service.
 * 
 * @author Alexander Wert
 *
 */
public class InfluxService {

	private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);

	/**
	 * Special project markers representing events that are not customer projects.
	 */
	public static String[] PROJECTS_TO_EXCLUDE = {
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_TRAINING_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_CONFERENCE_KEY),
			PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_OTHER_KEY) };

	/**
	 * Singleton instance.
	 */
	private static InfluxService instance;

	/**
	 * Nano factor.
	 */
	private static final long NANO = 1000000000L;

	/**
	 * Milli factor;
	 */
	private static final long MILLI = 1000L;

	/**
	 * Range where to generate weekends.
	 */
	private static final int EPSILON_WE_MONTHS = 12;

	/**
	 * Time of day where to place weekend and public holiday events into influxDB.
	 */
	private static final int WEEKEND_HOLIDAY_HOUR = 10;

	/**
	 * Time of day where to place customer projects and custom events into influxDB.
	 */
	private static final int PROJECT_HOUR = 12;

	/**
	 * Get singleton instance.
	 * 
	 * @return singleton instance.
	 */
	public synchronized static InfluxService getInstance() {
		if (null == instance) {
			instance = new InfluxService();
		}
		return instance;
	}

	/**
	 * InfluxDB connection.
	 */
	private InfluxDB influx;

	/**
	 * Last known entry in the calendar.
	 */
	private LocalDate lastCalendarEntry;

	private final Map<String, LocalDate> lastWeekendAndHolidayEntryMap = new HashMap<>();

	/**
	 * Constructor.
	 */
	private InfluxService() {
		influx = InfluxDBFactory.connect(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_URL_KEY),
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_USER_KEY),
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_PW_KEY));

		lastCalendarEntry = getLastCalendarEntry();

		for (String employee : getKnownEmployees()) {
			LocalDate date = getLastWeekEndAndHolidayEntryForEmployee(employee);
			if (date == null) {
				date = LocalDate.now();
			}
			lastWeekendAndHolidayEntryMap.put(employee, date);
		}

	}

	/**
	 * Retrieve the list of known employees from influxDB.
	 * 
	 * @return A sorted list of employees.
	 */
	public List<String> getKnownEmployees() {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".getKnownEmployees";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);

		try {
			// Logic
			String query = "SHOW TAG VALUES FROM \""
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY)
					+ "\" WITH KEY = \""
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_EMPLOYEE_KEY) + "\"";

			List<List<Object>> values = executeQuery(query);

			if (null == values) {
				return new ArrayList<String>();
			}

			List<String> employees = values.stream().map(p -> (String) p.get(1)).collect(Collectors.toList());
			Collections.sort(employees);
			return employees;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Retrieve a list of known customer projects.
	 * 
	 * @return A sorted list of known customer projects.
	 */
	public List<String> getKnownProjects() {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".getKnownProjects";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);

		try {
			// Logic
			String query = "SELECT DISTINCT "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY);

			List<List<Object>> values = executeQuery(query);

			if (null == values) {
				return new ArrayList<String>();
			}

			Set<String> toExclude = new HashSet<String>(Arrays.asList(PROJECTS_TO_EXCLUDE));

			List<String> projects = values.stream().map(p -> (String) p.get(1)).filter(p -> !toExclude.contains(p))
					.collect(Collectors.toList());
			Collections.sort(projects);
			return projects;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Retrieve a list of known unassigned customer projects.
	 * 
	 * @return A sorted list of known unassigned customer projects.
	 */
	public List<String> getKnownUnassignedProjects() {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".getKnownUnassignedProjects";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		try {
			// Logic
			String query = "SELECT DISTINCT "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY);

			List<List<Object>> values = executeQuery(query);

			if (null == values) {
				return new ArrayList<String>();
			}

			List<String> projects = values.stream().map(p -> (String) p.get(1)).filter(p -> !p.equals(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY)))
					.collect(Collectors.toList());
			Collections.sort(projects);
			return projects;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Assigns a set of projects.
	 * 
	 * @param projectAssignments
	 *            an array of project assignments.
	 */
	public void assignProjects(ProjectAssignment... projectAssignments) {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".assignProjects";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		try {
			// Logic
			BatchPoints batchPoints = BatchPoints
					.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
					.retentionPolicy(
							PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
					.consistency(ConsistencyLevel.ALL).build();
			for (ProjectAssignment projectAssignment : projectAssignments) {
				assignProject(projectAssignment, batchPoints);
			}
			writeToInflux(batchPoints);
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Creates an unassigned project.
	 * 
	 * @param project
	 *            Project to be created.
	 * @param from
	 *            from date
	 * @param to
	 *            to date
	 * @param notes
	 *            notes string
	 * @param color
	 *            color string in hex representation
	 */
	public void createUnassignedProject(String project, LocalDate from, LocalDate to, String notes, String color) {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".createUnassignedProject";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		s_this.setTag(JaegerUtil.T_PROJECT, project);
		s_this.setTag(JaegerUtil.T_FROM, from.toString());
		s_this.setTag(JaegerUtil.T_TO, to.toString());
		try {
			// Logic
			BatchPoints batchPoints = BatchPoints
					.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
					.retentionPolicy(
							PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
					.consistency(ConsistencyLevel.ALL).build();
			LocalDate current = from;
			int index = retrieveAvailableIndex(from, to);
			while (!current.isAfter(to)) {
				String yearMonth = getYearMonth(current);

				long nanoTime = getNanoTime(current, false);
				Builder pointBuilder = Point
						.measurement(PropertiesService.getInstance()
								.getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY))
						.time(nanoTime, TimeUnit.NANOSECONDS)
						.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY),
								String.valueOf(index))
						.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_YEAR_MONTH_KEY),
								yearMonth)
						.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_COLOR_KEY),
								color)
						.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY),
								project)
						.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_NOTES_KEY),
								notes);
				batchPoints.point(pointBuilder.build());
				current = current.plusDays(1);
			}

			writeToInflux(batchPoints);
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Deletes Project for the given deletion request.
	 * 
	 * @param request
	 *            deletion request
	 */
	public void deleteProject(ProjectDeleteRequest request) {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".deleteProject";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		s_this.setTag(JaegerUtil.T_EMPLOYEE, request.getEmployee());
		s_this.setTag(JaegerUtil.T_PROJECT, request.getProject());
		s_this.setTag(JaegerUtil.T_FROM, dateFromMillis(request.getStart()).toString());
		s_this.setTag(JaegerUtil.T_TO, dateFromMillis(request.getStart() + request.getDuration()).toString());
		try {
			// Logic
			LocalDate fromDate = dateFromMillis(request.getStart());
			LocalDate toDate = dateFromMillis(request.getStart() + request.getDuration()).minusDays(1);

			LOGGER.info("project to delete: " + request.getProject() + " for employee: " + request.getEmployee()
					+ " from: " + fromDate.toString() + " to: " + toDate.toString());
			String query = "SELECT DISTINCT "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY)
					+ " WHERE time >= " + getNanoTime(fromDate, false) + " AND time <= " + getNanoTime(toDate, false)
					+ " AND " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_EMPLOYEE_KEY)
					+ "='" + request.getEmployee() + "'";

			List<List<Object>> values = executeQuery(query);
			if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isEmpty()
					|| values.get(0).size() < 2 || values.get(0).get(1) == null) {
				return;
			}

			Set<String> projects = values.stream().map(row -> (String) row.get(1)).collect(Collectors.toSet());
			projects.remove(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY));
			projects.remove(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY));
			projects.remove(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY));
			if (projects.size() != 1) {
				return;
			}

			String projectInDB = projects.stream().findFirst().get();
			if (!projectInDB.equals(request.getProject())) {
				return;
			}

			String naProject = PropertiesService.getInstance()
					.getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY);
			double dailyRate = 0.0;
			double expenses = 0.0;
			String bookingStatus = PropertiesService.getInstance()
					.getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY);
			String notes = "";
			String color = PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_DEFAULT_KEY);

			ProjectAssignmentBuilder builder = new ProjectAssignmentBuilder();
			builder.employee(request.getEmployee()).project(naProject).status(bookingStatus).rate(dailyRate)
					.from(fromDate).to(toDate).daysOfWeek(new HashSet<>(Arrays.asList(DayOfWeek.values())))
					.skipHolidays(false).skipEvents(false).color(color).notes(notes).expenses(expenses);

			assignProjects(builder.build());
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Delete unassigned project.
	 * 
	 * @param request
	 *            deletion request
	 */
	public void deleteUnassignedProject(ProjectDeleteRequest request) {
		LocalDate fromDate = dateFromMillis(request.getStart());
		LocalDate toDate = dateFromMillis(request.getStart() + request.getDuration()).minusDays(1);
		deleteUnassignedProject(request.getProject(), fromDate, toDate, request.getEmployee());
	}

	/**
	 * Overwrites the given project with an empty entry.
	 * 
	 * @param project
	 *            project to overwrite
	 * @param from
	 *            from date
	 * @param to
	 *            to date
	 */
	public void deleteUnassignedProject(String project, LocalDate from, LocalDate to, String idx) {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".deleteUnassignedProject";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		s_this.setTag(JaegerUtil.T_PROJECT, project);
		s_this.setTag(JaegerUtil.T_INDEX, idx);
		s_this.setTag(JaegerUtil.T_FROM, from.toString());
		s_this.setTag(JaegerUtil.T_TO, to.toString());
		try {
			// Logic
			long nanoFromTime = getNanoTime(from, false);
			long nanoToTime = getNanoTime(to, false);

			// retrieve the existing entry for the given project
			String idxQuery = (idx == null) ? ""
					: (" AND " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY)
							+ "='" + idx + "'");
			String query = "SELECT time,"
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY) + ","
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY)
					+ " WHERE time >= " + String.valueOf(nanoFromTime) + " AND time <= " + String.valueOf(nanoToTime)
					+ idxQuery + " AND "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + "='"
					+ project + "' GROUP BY "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY);

			List<Series> seriesList = executeMultiSeriesQuery(query);

			for (Series series : seriesList) {
				List<List<Object>> values = series.getValues();
				if (null == values) {
					continue;
				}
				String index = series.getTags()
						.get(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY));

				LocalDate minDate = LocalDate.now().plusYears(1000);
				LocalDate maxDate = LocalDate.now().minusYears(1000);
				for (List<Object> row : values) {
					LocalDate current = LocalDate.parse((String) row.get(0), DateTimeFormatter.ISO_DATE_TIME);
					if (current.isBefore(minDate)) {
						minDate = current;
					}
					if (current.isAfter(maxDate)) {
						maxDate = current;
					}
				}

				long nanoMinTime = getNanoTime(minDate, false);
				long nanoMaxTime = getNanoTime(maxDate, false);

				String deleteQuery = "DELETE FROM "
						+ PropertiesService.getInstance()
								.getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY)
						+ " WHERE " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY)
						+ "='" + index + "' AND time >= " + String.valueOf(nanoMinTime) + " AND time <= "
						+ String.valueOf(nanoMaxTime);
				executeQuery(deleteQuery);

			}
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Creates weekend, public holidays and calendar entries into influx.
	 * 
	 * @param employee
	 *            the employee for whome to create the entries.
	 * @param from
	 *            from date
	 * @param to
	 *            to date
	 */
	public void createWeekEndsAndPublicHolidays(String employee, LocalDate from, LocalDate to) {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".createWeekEndsAndPublicHolidays";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		s_this.setTag(JaegerUtil.T_EMPLOYEE, employee);
		try {
			// Logic
			BatchPoints batchPoints = BatchPoints
					.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
					.retentionPolicy(
							PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
					.consistency(ConsistencyLevel.ALL).build();
			LocalDate current = from.minusMonths(EPSILON_WE_MONTHS);
			LocalDate lastWeekEndEntry = lastWeekendAndHolidayEntryMap.get(employee);
			LocalDate tmpLastCalendarEntry = lastCalendarEntry;
			while (!current.isAfter(to.plusMonths(EPSILON_WE_MONTHS))) {
				if (current.isAfter(lastWeekEndEntry)) {

					EventBuilder eventBuilder = new EventBuilder();
					eventBuilder.employee(employee);
					boolean workingDay = false;

					if (null != getPublicHoliday(current)) {
						eventBuilder.event(
								PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY));
						eventBuilder.color(
								PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_NA_KEY));
						eventBuilder.notes(getPublicHoliday(current).getName());
					} else if (current.getDayOfWeek().equals(DayOfWeek.SATURDAY)
							|| current.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
						eventBuilder.event(
								PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY));
						eventBuilder.color(
								PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_WE_KEY));
					} else {
						workingDay = true;
					}

					Point point = createProjectAssignmentInfluxPoint(eventBuilder.build(), current, true, workingDay);
					batchPoints.point(point);
					lastWeekEndEntry = current;
				}
				if (current.isAfter(tmpLastCalendarEntry)) {
					createCalendarEntry(batchPoints, current);
					tmpLastCalendarEntry = current;
				}

				current = current.plusDays(1);
			}

			writeToInflux(batchPoints);

			lastWeekendAndHolidayEntryMap.put(employee, lastWeekEndEntry);
			lastCalendarEntry = tmpLastCalendarEntry;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Writes monthly report data.
	 * 
	 * @param actualDataPoint
	 *            data to write
	 */
	public void enterMonthReportData(MonthReportDataPoint actualDataPoint) {
		// Monitoring
		String spanName = this.getClass().getSimpleName() + ".enterMonthReportData";
		final ActiveSpan s_this = JaegerUtil.getInstance().createNewActiveSpan(spanName);
		s_this.setTag(JaegerUtil.T_YEAR_MONTH, actualDataPoint.getYearMonth());
		s_this.setTag(JaegerUtil.T_EXPENSES, actualDataPoint.getExpenses());
		s_this.setTag(JaegerUtil.T_COSTS, actualDataPoint.getCosts());
		s_this.setTag(JaegerUtil.T_PROFIT, actualDataPoint.getProfit());
		s_this.setTag(JaegerUtil.T_RETRUN_ON_SALES, actualDataPoint.getRetrunOnSales());
		s_this.setTag(JaegerUtil.T_REVENUE, actualDataPoint.getRevenue());
		s_this.setTag(JaegerUtil.T_UTILIZATION, actualDataPoint.getUtilization());
		try {
			// Logic
			String yearMonth = actualDataPoint.getYearMonth();
			String[] ymArray = yearMonth.split("-");
			LocalDate date = LocalDate.of(Integer.parseInt(ymArray[0]), Integer.parseInt(ymArray[1]), 1);

			BatchPoints batchPoints = BatchPoints
					.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
					.retentionPolicy(
							PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
					.consistency(ConsistencyLevel.ALL).build();
			Builder pointBuilder = Point
					.measurement(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_VALIDATION_KEY))
					.time(getNanoTime(date, false), TimeUnit.NANOSECONDS)
					.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_YEAR_MONTH_KEY),
							yearMonth);
			MonthReportDataPoint estimatedDataPoint = calculateEstimatedDataPoint(yearMonth);
			writeReportData(pointBuilder, actualDataPoint);
			writeReportData(pointBuilder, estimatedDataPoint);
			batchPoints.point(pointBuilder.build());
			writeToInflux(batchPoints);
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Calculates estimated data point from data in influx.
	 * 
	 * @param yearMonth
	 *            the month for which the data point shell be calculated.
	 * @return data point
	 */
	private MonthReportDataPoint calculateEstimatedDataPoint(String yearMonth) {
		double costs = retrieveCostsReportData();
		String query = "SELECT 100*(count("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RATE_KEY) + ")-count("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_WORKINGDAY_KEY) + "))/count("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_WORKINGDAY_KEY) + "), sum("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_DAILY_EXPENSES_KEY) + "),sum("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RATE_KEY) + "), (sum("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RATE_KEY) + ")-"
				+ String.valueOf(costs) + "), (100-(100*" + String.valueOf(costs) + "/(sum("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RATE_KEY) + ")))) FROM "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY) + " WHERE "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_YEAR_MONTH_KEY) + "='"
				+ yearMonth + "' AND ("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RATE_KEY) + " > 0 OR "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_WORKINGDAY_KEY) + "=true)";
		List<List<Object>> values = executeQuery(query);
		if (null == values || null == values.get(0)) {
			MonthReportDataPoint defaultDP = MonthReportDataPoint.getDefault(yearMonth);
			defaultDP.setType(Type.ESTIMATED);
			return defaultDP;
		}
		List<Object> row = values.get(0);

		MonthReportDataPoint estimatedDataPoint = new MonthReportDataPoint();
		estimatedDataPoint.setYearMonth(yearMonth);
		estimatedDataPoint.setUtilization((Double) row.get(1));
		estimatedDataPoint.setExpenses((Double) row.get(2));
		estimatedDataPoint.setRevenue((Double) row.get(3));
		estimatedDataPoint.setProfit((Double) row.get(4));
		estimatedDataPoint.setRetrunOnSales((Double) row.get(5));
		estimatedDataPoint.setCosts(costs);
		estimatedDataPoint.setType(Type.ESTIMATED);
		return estimatedDataPoint;
	}

	/**
	 * Writes report data.
	 * 
	 * @param pointBuilder
	 *            point builder
	 * @param dataPoint
	 *            data point to write
	 */
	private void writeReportData(Builder pointBuilder, MonthReportDataPoint dataPoint) {
		pointBuilder.addField(
				dataPoint.getType().toString()
						+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_EXPENSES_KEY),
				dataPoint.getExpenses());
		pointBuilder.addField(
				dataPoint.getType().toString()
						+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_COSTS_KEY),
				dataPoint.getCosts());
		pointBuilder.addField(
				dataPoint.getType().toString()
						+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_REVENUE_KEY),
				dataPoint.getRevenue());
		pointBuilder.addField(
				dataPoint.getType().toString()
						+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROFIT_KEY),
				dataPoint.getProfit());
		pointBuilder.addField(
				dataPoint.getType().toString()
						+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_UTILIZATION_KEY),
				dataPoint.getUtilization());
		pointBuilder.addField(
				dataPoint.getType().toString()
						+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RETURN_KEY),
				dataPoint.getRetrunOnSales());
	}

	/**
	 * Calculates costs from historical data.
	 * 
	 * @return costs
	 */
	private double retrieveCostsReportData() {
		try {
			String query = "SELECT MEAN(" + Type.ACTUAL.toString()
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_COSTS_KEY) + ") FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_VALIDATION_KEY);
			List<List<Object>> values = executeQuery(query);

			return (Double) values.get(0).get(1);
		} catch (Exception e) {
			return 0.0;
		}
	}

	/**
	 * Retrieves the date of the last calendar entry in influxDB.
	 * 
	 * @return date
	 */
	private LocalDate getLastCalendarEntry() {
		String query = "SELECT LAST(value) FROM calendar";
		List<List<Object>> values = executeQuery(query);
		if (null == values || values.isEmpty()) {
			return LocalDate.now().minusMonths(EPSILON_WE_MONTHS);
		}
		return LocalDate.parse((String) values.get(0).get(0), DateTimeFormatter.ISO_DATE_TIME);
	}

	/**
	 * Retrieves the date of the last calendar entry in influxDB.
	 * 
	 * @return date
	 */
	private LocalDate getLastWeekEndAndHolidayEntryForEmployee(String employee) {
		String query = "SELECT LAST("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + ") FROM "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY) + " WHERE "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_EMPLOYEE_KEY) + " = '"
				+ employee + "' AND ("
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " = '"
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY) + "' OR "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " = '"
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY) + "')";
		List<List<Object>> values = executeQuery(query);
		if (null == values || values.isEmpty()) {
			return LocalDate.now();
		}
		return LocalDate.parse((String) values.get(0).get(0), DateTimeFormatter.ISO_DATE_TIME);
	}

	/**
	 * Create calendar entry.
	 * 
	 * @param batchPoints
	 *            batch points to add the points to
	 * @param current
	 *            current date.
	 */
	private void createCalendarEntry(BatchPoints batchPoints, LocalDate current) {
		Calendar date = Calendar.getInstance(Locale.GERMANY);
		date.setFirstDayOfWeek(Calendar.MONDAY);
		date.set(current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth(), WEEKEND_HOLIDAY_HOUR, 0, 0);
		long nanoTime = getNanoTime(current, true);
		Point pointM = Point.measurement("calendar").time(nanoTime, TimeUnit.NANOSECONDS).tag("type", "m")
				.addField("value", date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.GERMANY)).build();
		Point pointD = Point.measurement("calendar").time(nanoTime, TimeUnit.NANOSECONDS).tag("type", "d")
				.addField("value", String.valueOf(date.get(Calendar.DAY_OF_MONTH))).build();
		Point pointW = Point.measurement("calendar").time(nanoTime, TimeUnit.NANOSECONDS).tag("type", "w")
				.addField("value", String.valueOf(date.get(Calendar.WEEK_OF_YEAR))).build();

		batchPoints.point(pointM);
		batchPoints.point(pointD);
		batchPoints.point(pointW);
	}

	/**
	 * Retrieves the current available index for the given time range for unassigned
	 * project.
	 * 
	 * @param from
	 *            from date
	 * @param to
	 *            to date
	 * @return available index
	 */
	private int retrieveAvailableIndex(LocalDate from, LocalDate to) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance()
				.createNewActiveSpan(this.getClass().getSimpleName() + ".retrieveAvailableIndex");

		s_this.setTag(JaegerUtil.T_FROM, from.toString());
		s_this.setTag(JaegerUtil.T_TO, to.toString());

		try {
			// Logic
			long nanoFromTime = getNanoTime(from, false);
			long nanoToTime = getNanoTime(to, false);
			int index = 1;
			String query = "SELECT " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY)
					+ "," + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY)
					+ " FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY)
					+ " WHERE time >= " + String.valueOf(nanoFromTime) + " AND time <= " + String.valueOf(nanoToTime);

			List<List<Object>> values = executeQuery(query);

			if (null == values) {
				return index;
			}

			Set<Integer> indezes = values.stream().map(p -> Integer.parseInt((String) p.get(1)))
					.collect(Collectors.toSet());

			while (indezes.contains(index)) {
				index++;
			}
			return index;
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Retrieve public holiday for given date.
	 * 
	 * @param date
	 *            the date to retrieve the public holiday for.
	 * @return public holiday object or null if no public holiday is available at
	 *         that date.
	 */
	private Holiday getPublicHoliday(LocalDate date) {
		return HolidayService.getInstance().getHolidays(date.getYear()).get(date);
	}

	/**
	 * Retrieves dates for non project events including weekends, public holidays
	 * and any custom events.
	 * 
	 * @param from
	 *            from date
	 * @param to
	 *            to date
	 * @param employee
	 *            employee as a filter
	 * @return dates for non project events.
	 */
	private Set<LocalDate> getNonProjectEvents(LocalDate from, LocalDate to, String employee) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance()
				.createNewActiveSpan(this.getClass().getSimpleName() + ".getNonProjectEvents");

		s_this.setTag(JaegerUtil.T_EMPLOYEE, employee);
		s_this.setTag(JaegerUtil.T_FROM, from.toString());
		s_this.setTag(JaegerUtil.T_TO, to.toString());

		try {

			// Logic
			long fromNanoTime = getNanoTime(from, true);
			long toNanoTime = getNanoTime(to, true);

			String query = "SELECT "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
					+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY)
					+ " WHERE time >= " + String.valueOf(fromNanoTime) + " AND time <= " + String.valueOf(toNanoTime)
					+ " AND \"" + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_EMPLOYEE_KEY)
					+ "\"='" + employee + "' AND ";

			boolean first = true;
			Set<String> projectsToExclude = new HashSet<>(Arrays.asList(PROJECTS_TO_EXCLUDE));
			projectsToExclude.remove(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY));
			for (String prj : projectsToExclude) {

				if (!first) {
					query += " OR";
				} else {
					query += " (";
					first = false;
				}
				query += " \"" + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY)
						+ "\"='" + prj + "'";
			}
			query += " )";
			List<List<Object>> values = executeQuery(query);

			if (null == values) {
				return new HashSet<LocalDate>();
			}
			return values.stream().map(row -> LocalDate.parse((String) row.get(0), DateTimeFormatter.ISO_DATE_TIME))
					.collect(Collectors.toSet());
		} finally {
			// Monitoring
			s_this.deactivate();
		}

	}

	/**
	 * Processes a single project assignment.
	 * 
	 * @param projectAssignment
	 *            Project assignment to process.
	 * @param batchPoints
	 *            Influx Batchpoints to add the assignment to.
	 */
	private void assignProject(final ProjectAssignment projectAssignment, final BatchPoints batchPoints) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance()
				.createNewActiveSpan(this.getClass().getSimpleName() + ".assignProject");

		s_this.setTag(JaegerUtil.T_EMPLOYEE, projectAssignment.getEmployee());
		s_this.setTag(JaegerUtil.T_PROJECT, projectAssignment.getProject());
		s_this.setTag(JaegerUtil.T_FROM, projectAssignment.getFrom().toString());
		s_this.setTag(JaegerUtil.T_TO, projectAssignment.getTo().toString());

		try {
			// Logic
			LocalDate current = projectAssignment.getFrom();

			Set<LocalDate> datesToExclude = null;
			if (projectAssignment.isSkipEvents()) {
				datesToExclude = getNonProjectEvents(projectAssignment.getFrom(), projectAssignment.getTo(),
						projectAssignment.getEmployee());
			}

			while (!current.isAfter(projectAssignment.getTo())) {
				if (isDayToBeAssigned(projectAssignment, current, datesToExclude)) {
					ProjectAssignment tmpProjectAssignment = updateProjectAssignmentForProjectRemoval(projectAssignment,
							current);
					Point point = createProjectAssignmentInfluxPoint(tmpProjectAssignment, current, false, null);
					batchPoints.point(point);
				}
				current = current.plusDays(1);
			}
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Creates influx datapoint for a project assignment object.
	 * 
	 * @param projectAssignment
	 *            projects assignment for which the datapoint shall be created
	 * @param current
	 *            date
	 * @param weekendOrPublicHoliday
	 *            indicates which time of day to use for the data point
	 * @param workingDay
	 *            indicates whether working day shall be written or not. If null
	 *            this information will be skipped. Otherwise the boolean value is
	 *            written to influx.
	 * @return the influx data point.
	 */
	private Point createProjectAssignmentInfluxPoint(final ProjectAssignment projectAssignment, LocalDate current,
			boolean weekendOrPublicHoliday, Boolean workingDay) {
		String yearMonth = getYearMonth(current);
		long nanoTime = getNanoTime(current, weekendOrPublicHoliday);
		Builder pointBuilder = Point
				.measurement(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY))
				.time(nanoTime, TimeUnit.NANOSECONDS)
				.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_EMPLOYEE_KEY),
						projectAssignment.getEmployee())
				.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_YEAR_MONTH_KEY), yearMonth);
		if (null != projectAssignment.getProject()) {
			pointBuilder.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY),
					projectAssignment.getProject());
		}
		if (null != projectAssignment.getStatus()) {
			pointBuilder.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_STATUS_KEY),
					projectAssignment.getStatus());
		}
		if (null != projectAssignment.getColor()) {
			pointBuilder.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_COLOR_KEY),
					projectAssignment.getColor());
		}
		if (null != projectAssignment.getRate()) {
			pointBuilder.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_RATE_KEY),
					projectAssignment.getRate());
		}
		if (null != projectAssignment.getExpenses()) {
			pointBuilder.addField(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_DAILY_EXPENSES_KEY),
					projectAssignment.getExpenses());
		}
		if (null != projectAssignment.getNotes()) {
			pointBuilder.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_NOTES_KEY),
					projectAssignment.getNotes());
		}
		if (null != workingDay) {
			pointBuilder.addField(
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_WORKINGDAY_KEY),
					workingDay.booleanValue());
		}
		return pointBuilder.build();
	}

	/**
	 * Updates project assignment for project deletion. On weekends or holidays the
	 * old value is written.
	 * 
	 * @param projectAssignment
	 *            project assignment to update.
	 * @param current
	 *            date
	 */
	private ProjectAssignment updateProjectAssignmentForProjectRemoval(final ProjectAssignment projectAssignment,
			final LocalDate current) {
		Holiday holiday = getPublicHoliday(current);

		if (projectAssignment.getProject()
				.equals(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY))) {
			ProjectAssignmentBuilder builder = new ProjectAssignmentBuilder();
			builder.copy(projectAssignment);
			if (null != holiday) {
				builder.project(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY));
				builder.notes(holiday.getName());
				builder.color(PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_NA_KEY));
			} else if (current.getDayOfWeek().equals(DayOfWeek.SATURDAY)
					|| current.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
				builder.project(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY));
				builder.notes("");
				builder.color(PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_WE_KEY));
			}
			return builder.build();
		} else {
			return projectAssignment;
		}

	}

	/**
	 * Retrieve nano time for given date.
	 * 
	 * @param current
	 *            date
	 * @param weekendOrPublicHoliday
	 *            indicates which time of day to use for the data point
	 * @return nano timestamp
	 */
	public long getNanoTime(LocalDate current, boolean weekendOrPublicHoliday) {
		Calendar date = Calendar.getInstance();
		date.set(current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth(),
				weekendOrPublicHoliday ? WEEKEND_HOLIDAY_HOUR : PROJECT_HOUR, 0, 0);
		long nanoTime = (date.getTimeInMillis() / MILLI) * NANO;
		return nanoTime;
	}

	/**
	 * Retrieve date from the given epoch milli seconds.
	 * 
	 * @param milliseconds
	 *            milliseconds
	 * @return date
	 */
	private LocalDate dateFromMillis(long milliseconds) {
		Calendar date = Calendar.getInstance();
		date.setTimeInMillis(milliseconds);
		return date.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	/**
	 * Checks if the given date shall be assigned a project for the given project
	 * assignment.
	 * 
	 * @param projectAssignment
	 *            project assignment
	 * @param current
	 *            date
	 * @param datesToExclude
	 *            dates to be excluded
	 * @return true, if day can be assigned a project.
	 */
	private boolean isDayToBeAssigned(final ProjectAssignment projectAssignment, LocalDate current,
			Set<LocalDate> datesToExclude) {
		Holiday holiday = getPublicHoliday(current);
		return projectAssignment.getDaysOfWeek().contains(current.getDayOfWeek())
				&& (!projectAssignment.isSkipHolidays() || null == holiday)
				&& (!projectAssignment.isSkipEvents() || null == datesToExclude || !datesToExclude.contains(current));
	}

	/**
	 * Executes the given query statement against influx.
	 * 
	 * @param queryStr
	 *            query
	 * @return result row
	 */
	private List<List<Object>> executeQuery(String queryStr) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance()
				.createNewActiveSpan(this.getClass().getSimpleName() + ".executeQuery");

		s_this.log(queryStr);

		try {
			// Logic
			Query query = new Query(queryStr,
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY));

			QueryResult qResult = influx.query(query);
			List<Result> results = qResult.getResults();

			if (results.size() != 1) {
				return null;
			}
			Result result = results.get(0);
			if (null == result.getSeries() || result.getSeries().size() != 1) {
				return null;
			}
			Series series = result.getSeries().get(0);
			return series.getValues();
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Executes the given query statement against influx.
	 * 
	 * @param queryStr
	 *            query
	 * @return result row
	 */
	private List<Series> executeMultiSeriesQuery(String queryStr) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance()
				.createNewActiveSpan(this.getClass().getSimpleName() + ".executeMultiSeriesQuery");

		s_this.log(queryStr);

		try {
			// Logic
			Query query = new Query(queryStr,
					PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY));

			QueryResult qResult = influx.query(query);
			List<Result> results = qResult.getResults();

			if (results.size() != 1) {
				return null;
			}
			Result result = results.get(0);
			if (null == result.getSeries() || result.getSeries().size() < 1) {
				return null;
			}
			return result.getSeries();
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}

	/**
	 * Generates year-month string for the given date.
	 * 
	 * @param current
	 *            date
	 * @return year-month string
	 */
	private String getYearMonth(LocalDate current) {
		return String.valueOf(current.getYear()) + "-" + String.valueOf(current.getMonthValue());
	}

	/**
	 * Writes batch points to influx.
	 * 
	 * @param batchPoints
	 *            points to write.
	 */
	private void writeToInflux(BatchPoints batchPoints) {
		// Monitoring
		final ActiveSpan s_this = JaegerUtil.getInstance()
				.createNewActiveSpan(this.getClass().getSimpleName() + ".writeToInflux");

		s_this.setTag(JaegerUtil.T_POINTS_TO_WRITE, batchPoints.getPoints().size());

		try {
			influx.write(batchPoints);
		} finally {
			// Monitoring
			s_this.deactivate();
		}
	}
}
