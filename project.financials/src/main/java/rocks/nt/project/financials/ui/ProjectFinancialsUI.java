package rocks.nt.project.financials.ui;

import java.io.File;

import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.FileResource;
import com.vaadin.server.Page;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.AbsoluteLayout;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.BrowserFrame;
import com.vaadin.ui.Button;
import com.vaadin.ui.Image;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import rocks.nt.project.financials.services.AuthService;
import rocks.nt.project.financials.services.PropertiesService;

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
public class ProjectFinancialsUI extends UI {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String LOGO_APM_PNG_PATH = "/WEB-INF/images/logo.png";
	private static final String LOGO_APPFIN_PNG_PATH = "/WEB-INF/images/appfin.png";

	private static final int GRAFANA_MARGIN_RIGHT = 50;

	private static final int GRAFANA_FRAME_HEIGHT = 3000;

	@Override
	protected void init(VaadinRequest vaadinRequest) {
		if (!AuthService.isAuthenticated()) {
			showPublicComponent();
		} else {
			showPrivateComponent();
		}
	}

	public void showPrivateComponent() {
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
		
		// Logo-APM
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

		// Logout Button
		Button logoutButton = new Button("logout", e -> {AuthService.logOut();}); 
		logoutButton.addStyleName(ValoTheme.BUTTON_SMALL);
		logoutButton.addStyleName(ValoTheme.BUTTON_BORDERLESS_COLORED);
		rootLayout.addComponent(logoutButton, "right: 40px; top: 100px;");
		
		setContent(rootLayout);
	}

	public void showPublicComponent() {
		final VerticalLayout vLayout = new VerticalLayout();
		new LoginForm(vLayout);
		setContent(vLayout);
	}

	@WebServlet(urlPatterns = "/*", name = "ProjectFinancialsUIServlet", asyncSupported = true)
	@VaadinServletConfiguration(ui = ProjectFinancialsUI.class, productionMode = true)
	@ServletSecurity(value=@HttpConstraint(transportGuarantee=ServletSecurity.TransportGuarantee.CONFIDENTIAL))
	public static class ProjectFinancialsUIServlet extends VaadinServlet {

		/**
		 * 
		 */
		private static final long serialVersionUID = 8488639570234889259L;
	}

}
