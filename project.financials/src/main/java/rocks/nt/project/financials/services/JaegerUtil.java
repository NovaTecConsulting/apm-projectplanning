package rocks.nt.project.financials.services;

import com.uber.jaeger.Configuration;

import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;

public class JaegerUtil {

	public static final String T_INDEX = "index";
	public static final String T_PROJECT = "project";
	public static final String T_EVENT = "event";
	public static final String T_EMPLOYEE = "employee";
	public static final String T_FROM = "from";
	public static final String T_TO = "to";
	public static final String T_POINTS_TO_WRITE = "number of points";
	public static final String T_YEAR_MONTH = "year-month";
	public static final String T_EXPENSES = "expenses";
	public static final String T_COSTS = "costs";
	public static final String T_REVENUE = "revenue";
	public static final String T_PROFIT = "profit";
	public static final String T_UTILIZATION = "utilization";
	public static final String T_RETRUN_ON_SALES = "return on sales";
	public static final String T_USER_NAME = "user";

	private static JaegerUtil instance;

	public synchronized static JaegerUtil getInstance() {
		if (null == instance) {
			instance = new JaegerUtil();
		}

		return instance;
	}

	public static Tracer getTracer() {
		return getInstance().getConfig().getTracer();
	}

	private Configuration config;

	private JaegerUtil() {
		config = Configuration.fromEnv();
	}

	public Configuration getConfig() {
		return config;
	}

	@SuppressWarnings("resource")
	public ActiveSpan createNewActiveSpan(String spanName) {
		ActiveSpan currentActiveSpan = getTracer().activeSpan();
		if (null == currentActiveSpan) {
			currentActiveSpan = JaegerUtil.getTracer().buildSpan(spanName).startActive();
		}else {
			currentActiveSpan = JaegerUtil.getTracer().buildSpan(spanName).asChildOf(currentActiveSpan).startActive();
		}
		return currentActiveSpan;
	}
}
