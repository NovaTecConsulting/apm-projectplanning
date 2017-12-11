package rocks.nt.project.financials.services;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.server.VaadinService;

/**
 * Properties service.
 * 
 * @author awe
 *
 */
public class PropertiesService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesService.class);

	public static final int HEARTBEAT_INTERVAL_SEC = 120;
	
	public static final String APM_PROJECTS_HOME = "APM_PROJECTS_HOME";
	public static final String APM_LOGIN_USER = "apm.login.user";
	public static final String APM_LOGIN_PW = "apm.login.pw";
	public static final String GITHUB_ORGANIZATIONS_KEY = "github.valid.organization.ids";
	public static final String GITHUB_CLIENT_ID_KEY = "github.client.id";
	public static final String GITHUB_CLIENT_SECRET_KEY = "github.client.secret";
	public static final String GRAFANA_DASHBOARD_PROJECTS_KEY = "grafana.dashboard.projects.url";
	public static final String INFLUX_URL_KEY = "influx.url";
	public static final String INFLUX_USER_KEY = "influx.user";
	public static final String INFLUX_PW_KEY = "influx.pw";
	public static final String INFLUX_DATABASE_KEY = "influx.database";
	public static final String INFLUX_RETENTION_POLICY_KEY = "influx.retentionPolicy";
	public static final String INFLUX_M_PROJECTS_KEY = "influx.measurement.projects";
	public static final String INFLUX_M_UNASSIGNED_PROJECTS_KEY = "influx.measurement.unassignedProjects";
	public static final String INFLUX_T_EMPLOYEE_KEY = "influx.tag.employee";
	public static final String INFLUX_T_YEAR_MONTH_KEY = "influx.tag.yearMonth";
	public static final String INFLUX_T_INDEX_KEY = "influx.tag.uaproject.index";
	public static final String INFLUX_F_PROJECT_KEY = "influx.field.project";
	public static final String INFLUX_F_STATUS_KEY = "influx.field.bookingStatus";
	public static final String INFLUX_F_COLOR_KEY = "influx.field.color";
	public static final String INFLUX_F_RATE_KEY = "influx.field.dailyRate";
	public static final String INFLUX_F_DAILY_EXPENSES_KEY = "influx.field.dailyExpenses";
	public static final String INFLUX_F_NOTES_KEY = "influx.field.notes";
	public static final String INFLUX_F_WORKINGDAY_KEY = "influx.field.workingDay";
	public static final String INFLUX_V_PROJECT_WE_KEY = "influx.value.weekend";
	public static final String INFLUX_V_PROJECT_NA_KEY = "influx.value.notAvailable";
	public static final String INFLUX_V_PROJECT_REMOVED_KEY = "influx.value.projectRemoved";
	public static final String INFLUX_V_PROJECT_TRAINING_KEY = "influx.value.training";
	public static final String INFLUX_V_PROJECT_CONFERENCE_KEY = "influx.value.conference";
	public static final String INFLUX_V_PROJECT_OTHER_KEY = "influx.value.other";
	public static final String INFLUX_BOOKING_STATUS_HARD_KEY = "influx.value.hardBooked";
	public static final String INFLUX_BOOKING_STATUS_SOFT_KEY = "influx.value.softBooked";
	public static final String INFLUX_BOOKING_STATUS_REQUEST_KEY = "influx.value.request";
	
	public static final String INFLUX_M_VALIDATION_KEY = "influx.measurement.validation";
	public static final String INFLUX_F_EXPENSES_KEY = "influx.field.expenses";
	public static final String INFLUX_F_COSTS_KEY = "influx.field.costs";
	public static final String INFLUX_F_REVENUE_KEY = "influx.field.revenue";
	public static final String INFLUX_F_PROFIT_KEY = "influx.field.profit";
	public static final String INFLUX_F_UTILIZATION_KEY = "influx.field.utilization";
	public static final String INFLUX_F_RETURN_KEY = "influx.field.return";

	public static final String GRAFANA_COLOR_STATUS_HARD_KEY = "grafana.value.color.hardBooked";
	public static final String GRAFANA_COLOR_STATUS_SOFT_KEY = "grafana.value.color.softBooked";
	public static final String GRAFANA_COLOR_STATUS_REQUEST_KEY = "grafana.value.color.request";
	public static final String GRAFANA_COLOR_WE_KEY = "grafana.value.color.weekEnd";
	public static final String GRAFANA_COLOR_TRAINING_KEY = "grafana.value.color.training";
	public static final String GRAFANA_COLOR_CONFERENCE_KEY = "grafana.value.color.conference";
	public static final String GRAFANA_COLOR_OTHER_KEY = "grafana.value.color.other";
	public static final String GRAFANA_COLOR_NA_KEY = "grafana.value.color.notAvailable";
	public static final String GRAFANA_COLOR_UNASSIGNED_KEY = "grafana.value.color.unassigned";
	public static final String GRAFANA_COLOR_DEFAULT_KEY = "grafana.value.color.default";

	private static final String PROPERTY_PATH = "/WEB-INF/properties.conf";

	private static PropertiesService instance;

	public synchronized static PropertiesService getInstance() {
		if (null == instance) {
			instance = new PropertiesService();
		}
		return instance;
	}

	private final Properties defaultProperties = new Properties();
	private final Properties userProperties = new Properties();

	public static void main(String[] args) {
		PropertiesService.getInstance();
	}

	private PropertiesService() {
		String basepath = ".";
		if (null != VaadinService.getCurrent()) {
			basepath = VaadinService.getCurrent().getBaseDirectory().getAbsolutePath();
		}

		try {
			defaultProperties.load(new FileReader(basepath + PROPERTY_PATH));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		loadUserProperties();
		LOGGER.info("Using following properties:");
		LOGGER.info("-------------------------------------------------------");
		for (Object keyObj : defaultProperties.keySet()) {
			String key = (String) keyObj;
			LOGGER.info(key + " = " + getProperty(key));
		}
		LOGGER.info("-------------------------------------------------------");
	}

	private void loadUserProperties() {
		String path = System.getenv(APM_PROJECTS_HOME) + File.separator + "properties.conf";
		if (null != path) {
			try {
				userProperties.load(new FileReader(path));
			} catch (IOException e) {
				LOGGER.info("Couldn't load user properties from " + path + ". Using default properties instead.");
			}
		}

	}

	public String getProperty(String key) {
		String result = userProperties.getProperty(key);
		if (null == result) {
			return defaultProperties.getProperty(key);
		} else {
			return result;
		}
	}

}
