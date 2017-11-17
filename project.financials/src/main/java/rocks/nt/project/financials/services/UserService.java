package rocks.nt.project.financials.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserService {
	private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

	private static UserService instance;

	private static final String REMEMBERED_USERS_FILE = "rememberedUsers.txt";

	public synchronized static UserService getInstance() {
		if (null == instance) {
			instance = new UserService();
		}

		return instance;
	}

	/**
	 * Private constructor due to singleton.
	 */
	private UserService() {
		loadRememberedUsers();
	}

	private SecureRandom random = new SecureRandom();

	private Map<String, String> rememberedUsers = new HashMap<>();

	public boolean isAuthenticUser(String username, String password) {
		if (null == username || null == password) {
			return false;
		}
		return username.equals(PropertiesService.getInstance().getProperty(PropertiesService.APM_LOGIN_USER))
				&& password.equals(PropertiesService.getInstance().getProperty(PropertiesService.APM_LOGIN_PW));
	}

	public String rememberUser(String username) {
		String randomId = new BigInteger(130, random).toString(32);
		rememberedUsers.put(randomId, username);
		CompletableFuture.runAsync(this::updateRememberedUsersStorage);
		updateRememberedUsersStorage();
		return randomId;
	}

	public String getRememberedUser(String id) {
		return rememberedUsers.get(id);
	}

	public void removeRememberedUser(String id) {
		rememberedUsers.remove(id);
		CompletableFuture.runAsync(this::updateRememberedUsersStorage);
	}

	public synchronized void updateRememberedUsersStorage() {
		String path = System.getenv(PropertiesService.APM_PROJECTS_HOME);

		if (null != path) {
			path = path.endsWith(File.separator) ? path : path + File.separator;
			path = path + REMEMBERED_USERS_FILE;

			try (BufferedWriter writer = new BufferedWriter(new PrintWriter(path))) {
				boolean first = true;
				for (Entry<String, String> entry : rememberedUsers.entrySet()) {
					if (first) {
						first = false;
					} else {
						writer.newLine();
					}
					writer.append(entry.getKey());
					writer.append(":");
					writer.append(entry.getValue());
				}
			} catch (IOException e) {
				LOGGER.info("Couldn't load remembered users from " + path + ".");
			}
		}
	}

	private synchronized void loadRememberedUsers() {
		String path = System.getenv(PropertiesService.APM_PROJECTS_HOME);

		if (null != path) {
			path = path.endsWith(File.separator) ? path : path + File.separator;
			path = path + REMEMBERED_USERS_FILE;

			try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
				String line = reader.readLine();
				while (null != line) {
					String[] strArray = line.split(":");
					rememberedUsers.put(strArray[0], strArray[1]);
					line = reader.readLine();
				}
			} catch (IOException e) {
				LOGGER.info("Couldn't load remembered users from " + path + ".");
			}
		}
	}
}
