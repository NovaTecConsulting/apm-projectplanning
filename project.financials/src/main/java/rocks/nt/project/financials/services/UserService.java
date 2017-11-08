package rocks.nt.project.financials.services;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class UserService {

	private static SecureRandom random = new SecureRandom();

	private static Map<String, String> rememberedUsers = new HashMap<>();

	public static boolean isAuthenticUser(String username, String password) {
		if(null == username || null == password) {
			return false;
		}
		return username.equals(PropertiesService.getInstance().getProperty(PropertiesService.APM_LOGIN_USER))
				&& password.equals(PropertiesService.getInstance().getProperty(PropertiesService.APM_LOGIN_PW));
	}

	public static String rememberUser(String username) {
		String randomId = new BigInteger(130, random).toString(32);
		rememberedUsers.put(randomId, username);
		return randomId;
	}

	public static String getRememberedUser(String id) {
		return rememberedUsers.get(id);
	}

	public static void removeRememberedUser(String id) {
		rememberedUsers.remove(id);
	}
}