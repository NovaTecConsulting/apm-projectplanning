package rocks.nt.project.financials.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

import rocks.nt.project.financials.data.Holiday;
import rocks.nt.project.financials.data.ProjectAssignment;
import rocks.nt.project.financials.data.ProjectAssignment.EventBuilder;
import rocks.nt.project.financials.data.ProjectAssignment.ProjectAssignmentBuilder;

/**
 * Influx service.
 * 
 * @author Alexander Wert
 *
 */
public class InfluxService {

	/**
	 * Special project markers representing events that are not customer projects.
	 */
	private static String[] PROJECTS_TO_EXCLUDE = {
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
	private static final int EPSILON_WE_MONTHS = 6;

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
	public static InfluxService getInstance() {
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
	 * Constructor.
	 */
	private InfluxService() {
		influx = InfluxDBFactory.connect(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_URL_KEY),
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_USER_KEY),
				PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_PW_KEY));
	}

	/**
	 * Retrieve the list of known employees from influxDB.
	 * 
	 * @return A sorted list of employees.
	 */
	public List<String> getKnownEmployees() {
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
	}

	/**
	 * Retrieve a list of known customer projects.
	 * 
	 * @return A sorted list of known customer projects.
	 */
	public List<String> getKnownProjects() {
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
	}

	/**
	 * Retrieve a list of known unassigned customer projects.
	 * 
	 * @return A sorted list of known unassigned customer projects.
	 */
	public List<String> getKnownUnassignedProjects() {
		String query = "SELECT DISTINCT "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY);

		List<List<Object>> values = executeQuery(query);

		if (null == values) {
			return new ArrayList<String>();
		}

		List<String> projects = values.stream().map(p -> (String) p.get(1))
				.filter(p -> !p.equals(
						PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY)))
				.collect(Collectors.toList());
		Collections.sort(projects);
		return projects;
	}

	/**
	 * Assigns a set of projects.
	 * 
	 * @param projectAssignments
	 *            an array of project assignments.
	 */
	public void assignProjects(ProjectAssignment... projectAssignments) {
		BatchPoints batchPoints = BatchPoints
				.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
				.retentionPolicy(
						PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
				.consistency(ConsistencyLevel.ALL).build();
		for (ProjectAssignment projectAssignment : projectAssignments) {
			assignProject(projectAssignment, batchPoints);
		}
		influx.write(batchPoints);
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
					.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_COLOR_KEY), color)
					.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY),
							project)
					.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_NOTES_KEY), notes);
			batchPoints.point(pointBuilder.build());
			current = current.plusDays(1);
		}

		influx.write(batchPoints);
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
	 * @param color
	 *            color to use
	 */
	public void deleteUnassignedProject(String project, LocalDate from, LocalDate to, String color) {

		long nanoFromTime = getNanoTime(from, false);
		long nanoToTime = getNanoTime(to, false);

		// retrieve the existing entry for the given project
		String query = "SELECT time,"
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY) + ","
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
				+ PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY)
				+ " WHERE time >= " + String.valueOf(nanoFromTime) + " AND time <= " + String.valueOf(nanoToTime)
				+ " AND " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + "='"
				+ project + "'";

		List<List<Object>> values = executeQuery(query);

		if (null == values) {
			return;
		}

		// overwrite all cells for the given project
		BatchPoints batchPoints = BatchPoints
				.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
				.retentionPolicy(
						PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
				.consistency(ConsistencyLevel.ALL).build();
		for (List<Object> row : values) {
			LocalDate current = LocalDate.parse((String) row.get(0), DateTimeFormatter.ISO_DATE_TIME);
			String yearMonth = getYearMonth(current);
			long nanoTime = getNanoTime(current, false);
			Builder pointBuilder = Point
					.measurement(PropertiesService.getInstance()
							.getProperty(PropertiesService.INFLUX_M_UNASSIGNED_PROJECTS_KEY))
					.time(nanoTime, TimeUnit.NANOSECONDS)
					.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY),
							(String) row.get(1))
					.tag(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_YEAR_MONTH_KEY),
							yearMonth)
					.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_COLOR_KEY), color)
					.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY),
							PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY))
					.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_NOTES_KEY), "");
			batchPoints.point(pointBuilder.build());
		}
		if (!values.isEmpty()) {
			influx.write(batchPoints);
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
		BatchPoints batchPoints = BatchPoints
				.database(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_DATABASE_KEY))
				.retentionPolicy(
						PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_RETENTION_POLICY_KEY))
				.consistency(ConsistencyLevel.ALL).build();
		LocalDate current = from.minusMonths(EPSILON_WE_MONTHS);
		while (!current.isAfter(to.plusMonths(EPSILON_WE_MONTHS))) {
			EventBuilder eventBuilder = new EventBuilder();
			eventBuilder.employee(employee);
			boolean workingDay = false;

			if (null != getPublicHoliday(current)) {
				eventBuilder
						.event(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY));
				eventBuilder.color(PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_NA_KEY));
				eventBuilder.notes(getPublicHoliday(current).getName());
			} else if (current.getDayOfWeek().equals(DayOfWeek.SATURDAY)
					|| current.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
				eventBuilder
						.event(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY));
				eventBuilder.color(PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_WE_KEY));
			} else {
				workingDay = true;
			}

			Point point = createProjectAssignmentInfluxPoint(eventBuilder.build(), current, true, workingDay);
			batchPoints.point(point);

			createCalendarEntry(batchPoints, current);

			current = current.plusDays(1);
		}

		influx.write(batchPoints);
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
		Calendar date = Calendar.getInstance();
		date.set(current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth(), WEEKEND_HOLIDAY_HOUR, 0, 0);
		long nanoTime = getNanoTime(current, true);
		Point pointM = Point.measurement("calendar").time(nanoTime, TimeUnit.NANOSECONDS).tag("type", "m")
				.addField("value", date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.GERMANY)).build();
		Point pointD = Point.measurement("calendar").time(nanoTime, TimeUnit.NANOSECONDS).tag("type", "d")
				.addField("value", String.valueOf(date.get(Calendar.DAY_OF_MONTH))).build();
		batchPoints.point(pointM);
		batchPoints.point(pointD);
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
		long nanoFromTime = getNanoTime(from, false);
		long nanoToTime = getNanoTime(to, false);
		int index = 1;
		String query = "SELECT " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_INDEX_KEY)
				+ "," + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY) + " FROM "
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
		long fromNanoTime = getNanoTime(from, true);
		long toNanoTime = getNanoTime(to, true);

		String query = "SELECT " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY)
				+ " FROM " + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_M_PROJECTS_KEY)
				+ " WHERE time >= " + String.valueOf(fromNanoTime) + " AND time <= " + String.valueOf(toNanoTime)
				+ " AND \"" + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_T_EMPLOYEE_KEY)
				+ "\"='" + employee + "' AND ";

		boolean first = true;
		Set<String> projectsToExclude = new HashSet<>(Arrays.asList(PROJECTS_TO_EXCLUDE));
		projectsToExclude.remove(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_REMOVED_KEY));
		for (String prj : projectsToExclude) {
			
			if (!first) {
				query += " OR";
			} else {
				first = false;
			}
			query += " \"" + PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_PROJECT_KEY)
					+ "\"='" + prj + "'";
		}

		List<List<Object>> values = executeQuery(query);

		if (null == values) {
			return new HashSet<LocalDate>();
		}

		return values.stream().map(row -> LocalDate.parse((String) row.get(0), DateTimeFormatter.ISO_DATE_TIME))
				.collect(Collectors.toSet());
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
		LocalDate current = projectAssignment.getFrom();

		Set<LocalDate> datesToExclude = null;
		if (projectAssignment.isSkipEvents()) {
			datesToExclude = getNonProjectEvents(projectAssignment.getFrom(), projectAssignment.getTo(),
					projectAssignment.getEmployee());
		}

		while (!current.isAfter(projectAssignment.getTo())) {
			if (isDayToBeAssigned(projectAssignment, current, datesToExclude)) {
				ProjectAssignment tmpProjectAssignment = updateProjectAssignmentForProjectRemoval(projectAssignment, current);
				Point point = createProjectAssignmentInfluxPoint(tmpProjectAssignment, current, false, null);
				batchPoints.point(point);
			}
			current = current.plusDays(1);
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
			pointBuilder.addField(PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_F_EXPENSES_KEY),
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
				builder.project(
						PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_NA_KEY));
				builder.notes(holiday.getName());
				builder.color(
						PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_NA_KEY));
			} else if (current.getDayOfWeek().equals(DayOfWeek.SATURDAY)
					|| current.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
				builder.project(
						PropertiesService.getInstance().getProperty(PropertiesService.INFLUX_V_PROJECT_WE_KEY));
				builder.notes("");
				builder.color(
						PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_COLOR_WE_KEY));
			}
			return builder.build();
		}else {
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
	private long getNanoTime(LocalDate current, boolean weekendOrPublicHoliday) {
		Calendar date = Calendar.getInstance();
		date.set(current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth(),
				weekendOrPublicHoliday ? WEEKEND_HOLIDAY_HOUR : PROJECT_HOUR, 0, 0);
		long nanoTime = (date.getTimeInMillis() / MILLI) * NANO;
		return nanoTime;
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
}
