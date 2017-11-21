package rocks.nt.project.financials.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import rocks.nt.project.financials.services.InfluxService;

@Path("/projects")
public class Api {

	private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);

	// This method is called if HTML is request
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/delete")
	public void deleteProject(String json) {
		LOGGER.info("REST API called for project deletion");
		Gson gson = new Gson();
		ProjectDeleteRequest request = gson.fromJson(json, ProjectDeleteRequest.class);

		boolean isNumericEmployeeName = request.getEmployee().chars().allMatch( Character::isDigit );
		if(isNumericEmployeeName) {
			InfluxService.getInstance().deleteUnassignedProject(request);	
		}else {
			InfluxService.getInstance().deleteProject(request);	
		}
		
	}
}
