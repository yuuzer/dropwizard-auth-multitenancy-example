import dao.TenantDAO;
import dao.TokenDAO;
import dao.UserDAO;
import dao.WidgetDAO;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.db.PooledDataSourceFactory;
import io.dropwizard.hibernate.ScanningHibernateBundle;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import middleware.multitenancy.MultitenancyApplicationListener;
import middleware.security.CustomAuthFilter;
import middleware.security.CustomAuthenticator;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import resources.UserResource;
import resources.WidgetResource;

public class BusinessAPI extends Application<ExampleConfig> {
  public static void main(String[] args) throws Exception {
    new BusinessAPI().run(args);
  }

  private final ScanningHibernateBundle<ExampleConfig> hibernate = new ScanningHibernateBundle<ExampleConfig>("dao.entities") {
    @Override
    protected void configure(Configuration configuration) {
      // Register package so global filters in package-info.java get seen.
      configuration.addPackage("dao.entities");
      super.configure(configuration);
    }

    @Override
    public PooledDataSourceFactory getDataSourceFactory(ExampleConfig config) {
      return config.getDatabaseConfig();
    }
  };

  @Override
  public void initialize(Bootstrap<ExampleConfig> bootstrap) {
    bootstrap.addBundle(hibernate);
    bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html"));
  }

  @Override
  public void run(ExampleConfig config, Environment environment) throws Exception {
    SessionFactory sessionFactory = hibernate.getSessionFactory();
    WidgetDAO widgetDAO = new WidgetDAO(sessionFactory);
    UserDAO userDAO = new UserDAO(sessionFactory);
    TokenDAO tokenDAO = new TokenDAO(sessionFactory, userDAO);
    TenantDAO tenantDAO = new TenantDAO(sessionFactory);

    setupMultitenancy(environment, tenantDAO);
    setupAuth(environment, tokenDAO, userDAO);

    environment.jersey().register(new WidgetResource(widgetDAO));
    environment.jersey().register(new UserResource(userDAO));
  }

  private void setupMultitenancy(Environment environment, TenantDAO tenantDAO) {
    MultitenancyApplicationListener listener = new MultitenancyApplicationListener(tenantDAO, hibernate.getSessionFactory());

    environment.jersey().register(listener);
  }

  private void setupAuth(Environment environment, TokenDAO tokenDAO, UserDAO userDAO) {
    CustomAuthenticator authenticator = new UnitOfWorkAwareProxyFactory(hibernate)
      .create(CustomAuthenticator.class, new Class<?>[]{TokenDAO.class, UserDAO.class}, new Object[]{tokenDAO, userDAO});
    CustomAuthFilter filter = new CustomAuthFilter(authenticator);

    environment.jersey().register(new AuthDynamicFeature(filter));
    environment.jersey().register(RolesAllowedDynamicFeature.class);
  }
}
