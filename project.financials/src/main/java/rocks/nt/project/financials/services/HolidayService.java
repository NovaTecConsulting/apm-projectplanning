package rocks.nt.project.financials.services;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rocks.nt.project.financials.data.Holiday;

import com.google.gson.Gson;

/**
 * Service to retrieve public holidays.
 * 
 * @author Alexander Wert
 *
 */
public class HolidayService {
	/**
	 * Service URL.
	 */
	private static final String SERVICE_URL = "http://feiertage.jarmedia.de/api";

	/**
	 * Query parameter name.
	 */
	private static final String QUERY_PARAMETER = "jahr";

	/**
	 * We are interested in holidays for Baden-Württemberg.
	 */
	private static final String BADEN_WUERTTEMBERG = "BW";

	/**
	 * Date property.
	 */
	private static final String DATE_PROPERTY = "datum";

	/**
	 * Singleton service instance.
	 */
	private static HolidayService instance;

	/**
	 * Get singleton.
	 * 
	 * @return singleton service instance.
	 */
	public static HolidayService getInstance() {
		if (instance == null) {
			instance = new HolidayService();
		}
		return instance;
	}

	/**
	 * Http client.
	 */
	private OkHttpClient httpClient;

	/**
	 * Cache to not request the remote service repeatedly.
	 */
	private final Map<Integer, Map<LocalDate, Holiday>> holidaysMap = new HashMap<Integer, Map<LocalDate, Holiday>>();

	/**
	 * Constructor.
	 */
	private HolidayService() {
		httpClient = new OkHttpClient();

	}

	/**
	 * Retrieves a map of holidays to holiday objects.
	 * 
	 * @param year
	 *            the year for which to retrieve holidays.
	 * @return map of holidays to holiday objects.
	 */
	public Map<LocalDate, Holiday> getHolidays(int year) {
		if (!holidaysMap.containsKey(year)) {
			try {
				HttpUrl.Builder urlBuilder = HttpUrl.parse(SERVICE_URL).newBuilder();
				urlBuilder.addQueryParameter(QUERY_PARAMETER, String.valueOf(year));
				String url = urlBuilder.build().toString();

				Request request = new Request.Builder().url(url).build();

				Response response;

				response = httpClient.newCall(request).execute();

				String jsonResponse = response.body().string();

				Map<LocalDate, Holiday> result = parseJSONHolidays(jsonResponse);
				holidaysMap.put(year, result);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return holidaysMap.get(year);
	}

	/**
	 * Parse JSON response from remote service.
	 * 
	 * @param jsonResponse
	 *            the response
	 * @return the map.
	 */
	@SuppressWarnings("unchecked")
	private Map<LocalDate, Holiday> parseJSONHolidays(String jsonResponse) {
		Map<String, Map<String, Map<String, String>>> map = new Gson().fromJson(jsonResponse, HashMap.class);
		Map<String, Map<String, String>> bwMap = map.get(BADEN_WUERTTEMBERG);
		return bwMap.entrySet().stream().map(entry -> {
			return new Holiday(entry.getKey(), LocalDate.parse((String) entry.getValue().get(DATE_PROPERTY), DateTimeFormatter.ISO_DATE));
		}).collect(Collectors.toMap(Holiday::getDate, h -> h));
	}
}
