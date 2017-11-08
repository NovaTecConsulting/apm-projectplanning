package rocks.nt.project.financials.data;

import java.time.LocalDate;

/**
 * Holiday data object.
 * 
 * @author Alexander Wert
 *
 */
public class Holiday {

	/**
	 * Holiday name.
	 */
	private String name;
	
	/**
	 * Holiday date.
	 */
	private LocalDate date;

	/**
	 * Constructor.
	 * 
	 * @param name Holiday name.
	 * @param date Holiday date.
	 */
	public Holiday(String name, LocalDate date) {
		this.name = name;
		this.date = date;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the date
	 */
	public LocalDate getDate() {
		return date;
	}

	/**
	 * @param date
	 *            the date to set
	 */
	public void setDate(LocalDate date) {
		this.date = date;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Holiday [name=" + name + ", date=" + date + "]";
	}

}
