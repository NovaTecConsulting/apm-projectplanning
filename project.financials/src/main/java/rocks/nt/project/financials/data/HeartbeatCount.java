package rocks.nt.project.financials.data;

public class HeartbeatCount {
	private long lastHeartBeat;
	private int count;

	public HeartbeatCount(long lastHeartBeat, int count) {
		this.lastHeartBeat = lastHeartBeat;
		this.count = count;
	}

	public long getLastHeartBeatTimestamp() {
		return lastHeartBeat;
	}

	public void setLastHeartBeat(long lastHeartBeat) {
		this.lastHeartBeat = lastHeartBeat;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

}
