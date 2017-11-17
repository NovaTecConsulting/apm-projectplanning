package rocks.nt.project.financials.ui;

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

import rocks.nt.project.financials.services.AuthService;

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

	/**
	 * Constructor.
	 * 
	 * @param layout
	 *            Layout to put the Login form to.
	 */
	public LoginForm(VerticalLayout layout) {
		TextField username = new TextField("username");
		PasswordField password = new PasswordField("password");
		CheckBox rememberMe = new CheckBox("remember credentials");
		rememberMe.setValue(true);
		password.addShortcutListener(new ShortcutListener("Enter", ShortcutAction.KeyCode.ENTER, null) {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public void handleAction(Object sender, Object target) {
				onLogin(username.getValue(), password.getValue(), rememberMe.getValue());
			}
		});

		Button button = new Button("Login",
				e -> onLogin(username.getValue(), password.getValue(), rememberMe.getValue()));
		layout.addComponent(username);
		layout.setComponentAlignment(username, Alignment.TOP_CENTER);
		layout.addComponent(password);
		layout.setComponentAlignment(password, Alignment.TOP_CENTER);
		layout.addComponent(rememberMe);
		layout.setComponentAlignment(rememberMe, Alignment.TOP_CENTER);
		layout.addComponent(button);
		layout.setComponentAlignment(button, Alignment.TOP_CENTER);
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
	private void onLogin(String username, String password, boolean rememberMe) {
		if (AuthService.login(username, password, rememberMe)) {
			ProjectFinancialsUI ui = (ProjectFinancialsUI) UI.getCurrent();
			ui.showPrivateComponent();
		} else {
			Notification.show("Invalid credentials!", Notification.Type.ERROR_MESSAGE);
		}
	}

}
