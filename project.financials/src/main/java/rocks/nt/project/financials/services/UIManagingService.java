package rocks.nt.project.financials.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.vaadin.ui.UI;

import rocks.nt.project.financials.data.HeartbeatCount;
import rocks.nt.project.financials.ui.ProjectFinancialsUI;

public class UIManagingService {
	private static UIManagingService instance;

	public synchronized static UIManagingService getInstance() {
		if (null == instance) {
			instance = new UIManagingService();
		}
		return instance;
	}

	private static final int HEARTBEAT_THRESHOLD = 3;
	private final Set<ProjectFinancialsUI> activeUIs = new HashSet<>();

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Map<UI, HeartbeatCount> lastHeartBeats = new HashMap<>();

	private UIManagingService() {
		executor.scheduleAtFixedRate(() -> {
			synchronized (activeUIs) {
				for (ProjectFinancialsUI ui : activeUIs) {
					HeartbeatCount hbCount = lastHeartBeats.get(ui);
					if (null != hbCount) {
						if (ui.getLastHeartbeatTimestamp() == hbCount.getLastHeartBeatTimestamp()) {
							hbCount.setCount(hbCount.getCount() + 1);
						} else {
							hbCount.setLastHeartBeat(ui.getLastHeartbeatTimestamp());
							hbCount.setCount(0);
						}
						if (hbCount.getCount() > HEARTBEAT_THRESHOLD) {
							ui.access(() -> {
								ui.close();
							});
							unregisterUI(ui);
						}
					} else {
						lastHeartBeats.put(ui, new HeartbeatCount(ui.getLastHeartbeatTimestamp(), 0));
					}
				}
			}
		}, 0, PropertiesService.HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
	}

	public void updateAllLoggedInUserComponents() {
		List<ProjectFinancialsUI> uisToRemove = new ArrayList<>();
		for (ProjectFinancialsUI ui : activeUIs) {
			if (!ui.isAttached()) {
				uisToRemove.add(ui);
				continue;
			}
			ui.updateLoggedInUsersComponent();
		}
		synchronized (activeUIs) {
			activeUIs.removeAll(uisToRemove);
		}
	}

	public void registerUI(ProjectFinancialsUI ui) {
		synchronized (activeUIs) {
			activeUIs.add(ui);
			updateAllLoggedInUserComponents();
		}
	}

	public void unregisterUI(ProjectFinancialsUI ui) {
		synchronized (activeUIs) {
			activeUIs.remove(ui);
			updateAllLoggedInUserComponents();
		}
	}

	public Set<ProjectFinancialsUI> getAllUIs() {
		return Collections.unmodifiableSet(activeUIs);
	}
}
