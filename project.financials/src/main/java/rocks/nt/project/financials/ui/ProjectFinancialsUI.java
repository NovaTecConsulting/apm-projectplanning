package rocks.nt.project.financials.ui;

import java.io.File;

import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.FileResource;
import com.vaadin.server.Page;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinServlet;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.shared.ui.ui.Transport;
import com.vaadin.ui.AbsoluteLayout;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.BrowserFrame;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import rocks.nt.project.financials.data.User;
import rocks.nt.project.financials.services.AuthService;
import rocks.nt.project.financials.services.PropertiesService;
import rocks.nt.project.financials.services.UIManagingService;
import rocks.nt.project.financials.services.UserService;

/**
 * This UI is the application entry point. A UI may either represent a browser
 * window (or tab) or some part of a html page where a Vaadin application is
 * embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is
 * intended to be overridden to add component to the user interface and
 * initialize non-component functionality.
 */
@Theme("mytheme")
@PreserveOnRefresh
@Push(transport = Transport.LONG_POLLING)
public class ProjectFinancialsUI extends UI {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String LOGO_APM_PNG_PATH = "/WEB-INF/images/logo.png";
	private static final String LOGO_APPFIN_PNG_PATH = "/WEB-INF/images/appfin.png";

	private static final int GRAFANA_MARGIN_RIGHT = 50;

	private static final int GRAFANA_FRAME_HEIGHT = 3000;

	private HorizontalLayout loggedInUsersLayout = new HorizontalLayout();

	private User user;

	@Override
	protected void init(VaadinRequest vaadinRequest) {
		if (!AuthService.isAuthenticated()) {
			showPublicComponent();
		} else {
			showPrivateComponent();
		}
	}

	public void showPrivateComponent() {
		setUser();
		// Root Layout
		AbsoluteLayout rootLayout = new AbsoluteLayout();
		rootLayout.setWidth(String.valueOf(UI.getCurrent().getPage().getBrowserWindowWidth()) + "px");
		rootLayout.setHeight(
				String.valueOf(UI.getCurrent().getPage().getBrowserWindowHeight() + GRAFANA_FRAME_HEIGHT) + "px");

		// Logo-APM
		String basepath = VaadinService.getCurrent().getBaseDirectory().getAbsolutePath();
		FileResource resource = new FileResource(new File(basepath + LOGO_APM_PNG_PATH));
		Image image = new Image("", resource);
		rootLayout.addComponent(image, "right: 40px; top: 10px;");

		// Logo-AppFin
		FileResource resourceAppFinLogo = new FileResource(new File(basepath + LOGO_APPFIN_PNG_PATH));
		Image imageAppFin = new Image("", resourceAppFinLogo);
		imageAppFin.setSizeUndefined();
		rootLayout.addComponent(imageAppFin, "left: 40px; top: 10px;");

		final VerticalLayout vLayout = new VerticalLayout();

		// Grafana Dashboard
		final BrowserFrame browser = new BrowserFrame("", new ExternalResource(
				PropertiesService.getInstance().getProperty(PropertiesService.GRAFANA_DASHBOARD_PROJECTS_KEY)));
		
		browser.setWidth(
				String.valueOf(UI.getCurrent().getPage().getBrowserWindowWidth() - GRAFANA_MARGIN_RIGHT) + "px");
		browser.setHeight(String.valueOf(GRAFANA_FRAME_HEIGHT) + "px");
		final TabSheet tabSheet = new TabSheet();
		vLayout.addComponent(tabSheet);
		vLayout.setComponentAlignment(tabSheet, Alignment.TOP_CENTER);

		// Add Projects Tab
		new ProjectsTab(tabSheet);

		// Add Events Tab
		new EventTab(tabSheet);

		new UnassignedProjectsTab(tabSheet);

		vLayout.addComponent(browser);

		Page.getCurrent().addBrowserWindowResizeListener(event -> {
			browser.setWidth(String.valueOf(event.getWidth() - GRAFANA_MARGIN_RIGHT) + "px");
			browser.setHeight(String.valueOf(GRAFANA_FRAME_HEIGHT) + "px");
			rootLayout.setWidth(String.valueOf(UI.getCurrent().getPage().getBrowserWindowWidth()) + "px");
			rootLayout.setHeight(String.valueOf(GRAFANA_FRAME_HEIGHT) + "px");
		});

		rootLayout.addComponent(vLayout, "left: 0px; top: 60px;");

		MenuBar barmenu = new MenuBar();
		barmenu.addStyleName(ValoTheme.MENUBAR_BORDERLESS);
		MenuItem item = barmenu.addItem("", new ExternalResource(getUser().getAvatar_url() + "&s=32"), null);
		item.addItem("logout", VaadinIcons.SIGN_OUT, e -> {
			AuthService.logOut();
		});
		item.addItem("enter monthly data", VaadinIcons.ARCHIVE, e -> {
			addWindow(new RetrospectDataWindow());
		});
		rootLayout.addComponent(barmenu, "right: 40px; top: 100px;");

		// Hint Label
		Label hintLabel = new Label(
				"Entries can be deleted with <b>Ctrl</b> + <b>Shift</b> + <b>Click</b> on element in the dashboard!",
				ContentMode.HTML);
		rootLayout.addComponent(hintLabel, "right: 40px; top: 450px;");

		loggedInUsersLayout.setMargin(false);
		loggedInUsersLayout.setSpacing(true);
		rootLayout.addComponent(loggedInUsersLayout, "right: 350px; top: 10px;");

		setContent(rootLayout);

		UIManagingService.getInstance().registerUI(this);
	}

	public void showPublicComponent() {
		new LoginForm();
	}

	public void updateLoggedInUsersComponent() {
		access(() -> {
			loggedInUsersLayout.removeAllComponents();
			Label label = new Label("Active Users:");
			loggedInUsersLayout.addComponent(label);
			loggedInUsersLayout.setComponentAlignment(label, Alignment.MIDDLE_LEFT);
			for (User user : UserService.getInstance().getLoggedInUsers()) {
				ExternalResource resourceUserPic = new ExternalResource(user.getAvatar_url());
				Image userPicture = new Image(null, resourceUserPic);
				userPicture.setWidth(36.0f, Unit.PIXELS);
				userPicture.setDescription(user.getName());
				loggedInUsersLayout.addComponent(userPicture);
			}
		});
	}

	public User getUser() {
		return user;
	}

	private void setUser() {
		Object obj = VaadinSession.getCurrent().getAttribute(AuthService.SESSION_USER);
		if (null != obj && obj instanceof User) {
			user = (User) obj;
		}
	}

	@WebServlet(value = { "/app/*", "/VAADIN/*" }, name = "ProjectFinancialsUIServlet", asyncSupported = true)
	@VaadinServletConfiguration(ui = ProjectFinancialsUI.class, productionMode = true, heartbeatInterval = PropertiesService.HEARTBEAT_INTERVAL_SEC)
	@ServletSecurity(value = @HttpConstraint(transportGuarantee = ServletSecurity.TransportGuarantee.CONFIDENTIAL))
	public static class ProjectFinancialsUIServlet extends VaadinServlet {

		/**
		 * 
		 */
		private static final long serialVersionUID = 8488639570234889259L;

	}
}
