package rocks.nt.project.financials.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import rocks.nt.project.financials.data.User;

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

	private Map<String, User> rememberedUsers = new HashMap<>();

	public boolean isAuthenticUser(User user, String password) {
		if (null == user || null == password) {
			return false;
		}
		return user.getLogin().equals(PropertiesService.getInstance().getProperty(PropertiesService.APM_LOGIN_USER))
				&& password.equals(PropertiesService.getInstance().getProperty(PropertiesService.APM_LOGIN_PW));
	}

	public boolean isAuthenticUser(User user, Collection<String> organizations) {
		String validOrganizationsStr = PropertiesService.getInstance()
				.getProperty(PropertiesService.GITHUB_ORGANIZATIONS_KEY);
		String[] validOrgs = validOrganizationsStr.split(",");
		for (String validOrg : validOrgs) {
			if (organizations.contains(validOrg)) {
				return true;
			}
		}
		return false;
	}

	public String rememberUser(User user) {
		String randomId = new BigInteger(130, random).toString(32);
		rememberedUsers.put(randomId, user);
		CompletableFuture.runAsync(this::updateRememberedUsersStorage);
		updateRememberedUsersStorage();
		return randomId;
	}

	public User getRememberedUser(String id) {
		return rememberedUsers.get(id);
	}

	public void removeRememberedUser(String id) {
		rememberedUsers.remove(id);
		CompletableFuture.runAsync(this::updateRememberedUsersStorage);
	}

	public synchronized void updateRememberedUsersStorage() {
		String path = System.getenv(PropertiesService.APM_PROJECTS_HOME);
		Gson gson = new Gson();
		if (null != path) {
			path = path.endsWith(File.separator) ? path : path + File.separator;
			path = path + REMEMBERED_USERS_FILE;

			try (BufferedWriter writer = new BufferedWriter(new PrintWriter(path))) {
				boolean first = true;
				for (Entry<String, User> entry : rememberedUsers.entrySet()) {
					if (first) {
						first = false;
					} else {
						writer.newLine();
					}
					writer.append(entry.getKey());
					writer.append(";");
					String userJson = gson.toJson(entry.getValue());
					writer.append(userJson);
				}
			} catch (IOException e) {
				LOGGER.info("Couldn't load remembered users from " + path + ".");
			}
		}
	}

	private synchronized void loadRememberedUsers() {
		String path = System.getenv(PropertiesService.APM_PROJECTS_HOME);
		Gson gson = new Gson();
		if (null != path) {
			path = path.endsWith(File.separator) ? path : path + File.separator;
			path = path + REMEMBERED_USERS_FILE;

			try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
				String line = reader.readLine();
				while (null != line) {
					int splitIndex = line.indexOf(";");
					String key = line.substring(0, splitIndex);
					String userJson = line.substring(splitIndex + 1, line.length());
					User user = gson.fromJson(userJson, User.class);
					rememberedUsers.put(key, user);
					line = reader.readLine();
				}
			} catch (IOException e) {
				LOGGER.info("Couldn't load remembered users from " + path + ".");
			}
		}
	}

	public Set<User> getLoggedInUsers() {
		return Collections.unmodifiableSet(UIManagingService.getInstance().getAllUIs().stream().map(ui -> ui.getUser())
				.filter(user -> user != null).collect(Collectors.toSet()));
	}

}
