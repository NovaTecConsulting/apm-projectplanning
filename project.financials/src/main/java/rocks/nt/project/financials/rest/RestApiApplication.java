package rocks.nt.project.financials.rest;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/rest")
public class RestApiApplication extends Application{
	
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> set = new HashSet<>();
        set.add(Api.class);
        set.add(CORSResponseFilter.class);
        return set;
    }
}
