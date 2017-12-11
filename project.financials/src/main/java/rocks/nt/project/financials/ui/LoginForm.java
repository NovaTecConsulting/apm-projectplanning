package rocks.nt.project.financials.ui;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.vaadin.addon.oauthpopup.OAuthListener;
import org.vaadin.addon.oauthpopup.OAuthPopupButton;
import org.vaadin.addon.oauthpopup.buttons.GitHubButton;

import com.github.scribejava.apis.GitHubApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Token;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.github.scribejava.core.oauth.OAuthService;
import com.google.gson.Gson;
import com.googlecode.gentyref.TypeToken;
import com.vaadin.event.ShortcutAction;
import com.vaadin.event.ShortcutListener;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Notification;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import rocks.nt.project.financials.data.GitHubOrganization;
import rocks.nt.project.financials.data.User;
import rocks.nt.project.financials.services.AuthService;
import rocks.nt.project.financials.services.PropertiesService;

/**
 * Login form.
 * 
 * @author awe
 *
 */
public class LoginForm extends CustomComponent {

	/**
	* 
	*/
	private static final long serialVersionUID = -5891997305801526210L;

	public static final String GITHUB_USER_URL = "https://api.github.com/user";
	public static final String GITHUB_USERORG_URL = "https://api.github.com/user/orgs";

	private CheckBox rememberMe;

	/**
	 * Constructor.
	 * 
	 * @param layout
	 *            Layout to put the Login form to.
	 */
	public LoginForm() {
		final VerticalLayout layout = new VerticalLayout();

		rememberMe = new CheckBox("remember credentials");
		rememberMe.setValue(true);

		Button button = new Button("Login as Admin", e -> showAdminLogin());
		button.addStyleName(ValoTheme.BUTTON_LINK);
		addGitHubButton(layout);
		layout.addComponent(rememberMe);
		layout.setComponentAlignment(rememberMe, Alignment.TOP_CENTER);
		layout.addComponent(button);
		layout.setComponentAlignment(button, Alignment.TOP_CENTER);

		UI.getCurrent().setContent(layout);
	}

	public void showAdminLogin() {
		final VerticalLayout layout = new VerticalLayout();

		TextField username = new TextField("username");
		PasswordField password = new PasswordField("password");
		rememberMe = new CheckBox("remember credentials");
		rememberMe.setValue(true);
		password.addShortcutListener(new ShortcutListener("Enter", ShortcutAction.KeyCode.ENTER, null) {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public void handleAction(Object sender, Object target) {
				onAdminLogin(username.getValue(), password.getValue(), rememberMe.getValue());
			}
		});

		Button button = new Button("Login",
				e -> onAdminLogin(username.getValue(), password.getValue(), rememberMe.getValue()));

		layout.addComponent(username);
		layout.setComponentAlignment(username, Alignment.TOP_CENTER);
		layout.addComponent(password);
		layout.setComponentAlignment(password, Alignment.TOP_CENTER);
		layout.addComponent(rememberMe);
		layout.setComponentAlignment(rememberMe, Alignment.TOP_CENTER);
		layout.addComponent(button);
		layout.setComponentAlignment(button, Alignment.TOP_CENTER);
		OAuthPopupButton gitHubButton = addGitHubButton(layout);
		gitHubButton.addStyleName(ValoTheme.BUTTON_LINK);
		UI.getCurrent().setContent(layout);
	}

	/**
	 * Login user.
	 * 
	 * @param username
	 *            the username
	 * @param password
	 *            the password
	 * @param rememberMe
	 *            the remember me indicator
	 */
	private void onAdminLogin(String username, String password, boolean rememberMe) {
		User user = new User();
		user.setLogin(username);
		user.setAvatar_url(User.APM_AVATAR_URL);
		user.setName("APM Technical User");
		if (AuthService.login(user, password, rememberMe)) {
			ProjectFinancialsUI ui = (ProjectFinancialsUI) UI.getCurrent();
			ui.showPrivateComponent();
		} else {
			Notification.show("Invalid credentials!", Notification.Type.ERROR_MESSAGE);
		}
	}

	private OAuthPopupButton addGitHubButton(VerticalLayout layout) {
		String clientId = PropertiesService.getInstance().getProperty(PropertiesService.GITHUB_CLIENT_ID_KEY);
		String clientSecret = PropertiesService.getInstance().getProperty(PropertiesService.GITHUB_CLIENT_SECRET_KEY);
		OAuthPopupButton gitHubButton = new GitHubButton(clientId, clientSecret);
		gitHubButton.setCaption("Login with GitHub");
		gitHubButton.getOAuthPopupConfig().setScope("read:org");
		gitHubButton.setPopupWindowFeatures("resizable,width=600,height=500");
		gitHubButton.setWidth("200px");

		layout.addComponent(gitHubButton);
		layout.setComponentAlignment(gitHubButton, Alignment.TOP_CENTER);
		gitHubButton.addOAuthListener(new GitHubOAuthListener());
		return gitHubButton;
	}

	private class GitHubOAuthListener implements OAuthListener {
		@Override
		public void authSuccessful(final Token token, final boolean isOAuth20) {
			try {
				User gitHubUser = getUser(token);
				Set<String> orgIds = getUserOrganizationIds(token);

				if (AuthService.loginGitHubUser(gitHubUser, orgIds, rememberMe.getValue())) {
					ProjectFinancialsUI ui = (ProjectFinancialsUI) UI.getCurrent();
					ui.access(() -> ui.showPrivateComponent());
				} else {
					Notification.show("Invalid credentials!", Notification.Type.ERROR_MESSAGE);
				}

			} catch (Exception e) {
				Notification.show("Authorization denied! " + e.getMessage(), Notification.Type.ERROR_MESSAGE);
			}
		}

		@Override
		public void authDenied(String reason) {
			Notification.show("Authorization denied! " + reason, Notification.Type.ERROR_MESSAGE);
		}
	}

	private Set<String> getUserOrganizationIds(final Token token) throws IOException {
		Gson gson = new Gson();
		String responseOrgs = doGitHubApiRequest(GITHUB_USERORG_URL, token);
		Type listType = new TypeToken<List<GitHubOrganization>>() {
		}.getType();
		List<GitHubOrganization> organizations = gson.fromJson(responseOrgs, listType);
		Set<String> orgIds = organizations.stream().map(o -> o.getId()).collect(Collectors.toSet());
		return orgIds;
	}

	private User getUser(final Token token) throws IOException {
		Gson gson = new Gson();
		String responseUser = doGitHubApiRequest(GITHUB_USER_URL, token);
		return gson.fromJson(responseUser, User.class);
	}

	private String doGitHubApiRequest(String url, Token token) throws IOException {
		String clientId = PropertiesService.getInstance().getProperty(PropertiesService.GITHUB_CLIENT_ID_KEY);
		String clientSecret = PropertiesService.getInstance().getProperty(PropertiesService.GITHUB_CLIENT_SECRET_KEY);
		final ServiceBuilder sb = new ServiceBuilder().apiKey(clientId).apiSecret(clientSecret);
		final OAuthService service = sb.build(GitHubApi.instance());

		final OAuthRequest request = new OAuthRequest(Verb.GET, url, service);
		((OAuth20Service) service).signRequest((OAuth2AccessToken) token, request);
		Response resp = request.send();
		return resp.getBody();
	}

}
