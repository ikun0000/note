# SpringBoot集成Spring Security

首先导入Spring Security的依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```



然后定义配置类开启Spring Security并继承`WebSecurityConfigurerAdapter`配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()        // 对请求进行认证
                .anyRequest().authenticated()       // 所有请求都要认证
                .and()
                .formLogin();         // 使用表单验证
    }
}
```

控制台会输出认证key，默认用户名是user

```
Using generated security password: b054b2e4-5360-4f8a-b254-2610e6b2c79d
```



SpringSecurity登陆表单

```html
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:th="https://www.thymeleaf.org"
      xmlns:sec="https://www.thymeleaf.org/thymeleaf-extras-springsecurity3">
    <head>
        <title>Spring Security Example </title>
    </head>
    <body>
        <div th:if="${param.error}">
            Invalid username and password.
        </div>
        <div th:if="${param.logout}">
            You have been logged out.
        </div>
        <form th:action="@{/login}" method="post">
            <div><label> User Name : <input type="text" name="username"/> </label></div>
            <div><label> Password: <input type="password" name="password"/> </label></div>
            <div><input type="submit" value="Sign In"/></div>
        </form>
    </body>
</html>
```

默认情况下登陆失败SpringSecurity会返回到登陆也并在Model设置`param.error`。如果是推出成功后也会跳转到登陆页并在Model添加了`param.logout`

默认情况下SpringSecurity会开启CSRF保护，如果不关闭，要在表单提交时带上CSRF token，只需要在form表单加上一个hidden属性即可

```html
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
```

设置CSRF token保存在cookie中

```java
@EnableWebSecurity
public class WebSecurityConfig extends
        WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            );
    }
}
```





SpringSecurity的原理是使用过滤器完成验证的，过滤器链大致如下

![](./img/d.png)



### 自定义用户认证

#### 从内存中设置用户名密码

从内存中读取用户信息要实现`WebSecurityConfigurerAdapter`的签名为`configure(AuthenticationManagerBuilder auth)`的方法，其中使用`inMemoryAuthentication()`设置从内存中用户信息，然后使用签名为`withUser(UserDetails userDetails)`的方法设置用户信息

```java
@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication()
        .withUser(new User("root",
                           passwordEncoder.encode("123456"),
                           true,
                           true,
                           true,
                           true,
                           AuthorityUtils.commaSeparatedStringToAuthorityList("USER_ADMIN")))
        .withUser(new User("user",
                           passwordEncoder.encode("654321"),
                           true,
                           true,
                           true,
                           true,
                           AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_USER")));
}
```



#### 实现`UserDetailsService`

首先实现`UserDetailsService`接口重写`loadUserByUsername`的用户认证逻辑

这个方法返回`UserDetails`接口，这里返回`org.springframework.security.core.userdetails.User`，是他的一个实现类，注意不要导错包

```java
@Configuration
public class LevelUserDetailsService implements UserDetailsService {

    @Autowired
    private LevelUserService levelUserService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LevelUser levelUser = levelUserService.getLevelUserByName(username);
        if (levelUser == null) {
            return null;
        }

        return new User(username,       // 用户名
                levelUser.getPassword(),    // 密码
                true,               // 该用户是否开启
                true,       // 用户没有过期
                true,   // 用户证书没有过期
                true,       // 用户没有被锁定
                AuthorityUtils.commaSeparatedStringToAuthorityList(levelUser.getRole()));   // 用户有哪些权限
    }
}

```

`AuthorityUtils.commaSeparatedStringToAuthorityList(String)`用来将字符串转化为权限



然乎自定义密码规则，使用SpringSecurity推荐的

```java
@Bean
public PasswordEncoder passwordEncoderBean() {
    return new BCryptPasswordEncoder();
}
```

自定义html等路页面，登陆的用户名密码表单的name属性要和`UsernamePasswordAuthenticationFilter`定义的一样

```java
public class UsernamePasswordAuthenticationFilter extends
		AbstractAuthenticationProcessingFilter {
	// ~ Static fields/initializers
	// =====================================================================================

	public static final String SPRING_SECURITY_FORM_USERNAME_KEY = "username";
	public static final String SPRING_SECURITY_FORM_PASSWORD_KEY = "password";

	private String usernameParameter = SPRING_SECURITY_FORM_USERNAME_KEY;
	private String passwordParameter = SPRING_SECURITY_FORM_PASSWORD_KEY;
	private boolean postOnly = true;

	// ~ Constructors
	// ===================================================================================================
...
```

添加配置

```java
@Component
public class MyAuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();
        writer.println("登陆失败!");
    }
}
```

```java
@Override
protected void configure(HttpSecurity http) throws Exception {
    http.authorizeRequests()        // 对请求进行认证
        .antMatchers("/", "/index", "/index.html", "/form", "/constraint").permitAll()      // 允许放行的URI
        .anyRequest().authenticated()       // 所有请求都要认证
        .and()
        .formLogin()         // 使用表单验证
        .loginPage("/form")         // 定义认证页
        .loginProcessingUrl("/constraint")      // 定义认证逻辑的URI
        .failureHandler(myAuthenticationFailureHandler)
        .successForwardUrl("/index")
        .and()
        .csrf().disable();          // 关闭CSRF否则表单登陆失败
}
```

`.formLogin()`可以设置很多东西，比如：

* `.successHandler()`成功的处理器， 传入`AuthenticationSuccessHandler`的实现
* `.successForwardUrl()`成功后重定向的URL
* `.failureHandler()`失败的处理器，传入`AuthenticationFailureHandler`的实现
* `.failureForwardUrl()`登陆失败后的URL



成功登陆后在其他地方（controller， service）注入`Authentication `会有这些信息

```json
{
  "authorities": [
    {
      "authority": "admin"
    }
  ],
  "details": {
    "remoteAddress": "0:0:0:0:0:0:0:1",
    "sessionId": "8D50BAF811891F4397E21B4B537F0544"
  },
  "authenticated": true,
  "principal": {
    "password": null,
    "username": "mrbird",
    "authorities": [
      {
        "authority": "admin"
      }
    ],
    "accountNonExpired": true,
    "accountNonLocked": true,
    "credentialsNonExpired": true,
    "enabled": true
  },
  "credentials": null,
  "name": "mrbird"
}
```





如果`WebSecurityConfigurerAdapter`使用这个方法

```java
@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.inMemoryAuthentication().withUser("aa").password("123456").authorities("level1");
}
```

在内存或者数据库读取用户



要使用默认的从JDBC验证用户名密码要在数据库有这些表

```sql
create table users(
    username varchar_ignorecase(50) not null primary key,
    password varchar_ignorecase(50) not null,
    enabled boolean not null
);

create table authorities (
    username varchar_ignorecase(50) not null,
    authority varchar_ignorecase(50) not null,
    constraint fk_authorities_users foreign key(username) references users(username)
);
create unique index ix_auth_username on authorities (username,authority);
```



### 退出设置

```java
http.logout()           // 定义注销
    .logoutUrl("/logout")       // 注销的URL
    .logoutSuccessUrl("/index")     // 注销成功后跳转到的地址
    .deleteCookies("JSRSESSION");       // 注销删除COOKIE
	.clearAuthentication(true);
//  .logoutSuccessUrl(LogoutSuccessHandler)   // 退出成功后的handler
```



### Session管理

 Session超时时间也就是用户登录的有效时间。要设置Session超时时间很简单，只需要在配置文件中添加：

```yaml
server:
  session:
    timeout: 3600
```



```java
http.sessionManagement()        // 开启session管理
    .maximumSessions(5)     // 一个用户最多持有的session数量
    .expiredUrl("/form")    // session失效后跳转地址
    .maxSessionsPreventsLogin(true);    // 如果session达到maximumSessions就不允许登陆
//	..expiredSessionStrategy()  session过期后的Strategy，传入SessionInformationExpiredStrategy的实现类
```



##### 使用Spring Session和Redis管理Session

导入依赖

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

配置redis地址和session存储方式

```yaml
spring:
  thymeleaf:
    cache: false
  session:
    store-type: REDIS
  redis:
    host: 10.10.10.246
    port: 6379
    database: 1
    jedis:
      pool:
        max-idle: 8
        min-idle: 0
        max-active: 8
        max-wait: 1
    timeout: 3000
```

之后web项目的session就都会存在redis里面了



其他操作

` *SessionRegistry* ` 包含了一些使用的操作Session的方法，比如： 

 踢出用户（让Session失效） 

```java
String currentSessionId = request.getRequestedSessionId();
sessionRegistry.getSessionInformation(sessionId).expireNow();
```

 获取所有Session信息： 

```java
List<Object> principals = sessionRegistry.getAllPrincipals();
```



### 记住我功能

导入依赖

```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.19</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

找到`JdbcDaoSupport`按住Ctrl + Alt + B查看实现类，找到`JdbcTokenRepositoryImpl`

不配置就会保存在内存中

```java
public class JdbcTokenRepositoryImpl extends JdbcDaoSupport implements
		PersistentTokenRepository {
	// ~ Static fields/initializers
	// =====================================================================================

	/** Default SQL for creating the database table to store the tokens */
	public static final String CREATE_TABLE_SQL = "create table persistent_logins (username varchar(64) not null, series varchar(64) primary key, "
			+ "token varchar(64) not null, last_used timestamp not null)";
	/** The default SQL used by the <tt>getTokenBySeries</tt> query */
	public static final String DEF_TOKEN_BY_SERIES_SQL = "select username,series,token,last_used from persistent_logins where series = ?";
	/** The default SQL used by <tt>createNewToken</tt> */
	public static final String DEF_INSERT_TOKEN_SQL = "insert into persistent_logins (username, series, token, last_used) values(?,?,?,?)";
	/** The default SQL used by <tt>updateToken</tt> */
	public static final String DEF_UPDATE_TOKEN_SQL = "update persistent_logins set token = ?, last_used = ? where series = ?";
	/** The default SQL used by <tt>removeUserTokens</tt> */
	public static final String DEF_REMOVE_USER_TOKENS_SQL = "delete from persistent_logins where username = ?";

	// ~ Instance fields
	// 
```

复制建表语句去数据库执行

```sql
create table persistent_logins 
(
username varchar(64) not null, 
series varchar(64) primary key, 
token varchar(64) not null, 
last_used timestamp not null
);
```

配置`JdbcTokenRepositoryImpl`

```java
@Autowired
private DataSource dataSource;

@Bean
public PersistentTokenRepository persistentTokenRepositoryBean() {
    JdbcTokenRepositoryImpl jdbcTokenRepository = new JdbcTokenRepositoryImpl();
    jdbcTokenRepository.setDataSource(dataSource);
    jdbcTokenRepository.setCreateTableOnStartup(false);     // 不在执行建表语句
    return jdbcTokenRepository;
}
```

在input表单设置记住我选框的name为`remember-me`或者自己修改name属性配置

配置remember me

```java
http.rememberMe()       // 添加记住我功能
    .tokenRepository(persistentTokenRepositoryBean())    // 配置 token 持久化仓库
    .tokenValiditySeconds(3600)           // remember 过期时间，单为秒
    .userDetailsService(levelUserDetailsService)    // 处理自动登录逻辑
    .rememberMeParameter("remember_me");            // 表单name属性
```





### 权限控制

```java
http.authorizeRequests()        // 对请求进行认证
    .antMatchers("/level1/*").hasAnyAuthority("level1", "admin")	// 允许一个资源只能有对应劝降的人访问
    .antMatchers("/level2/*").hasAnyAuthority("level2", "admin")
    .antMatchers("/level3/*").hasAnyAuthority("level3", "admin")
    .antMatchers("/", "/index", "/index.html", "/form", "/constraint", "/logout").permitAll()      // 允许放行的URI
    .anyRequest().authenticated()       // 所有请求都要认证
    .and();
```

* `hasAnyAuthority(String...)`设置权限列表
* `hasAnyRole(String...)`设置一个权限
* ``hasAuthority(String)`设置角色列表
* ``hasRole(String)`设置一个角色
* `hasIpAddress()`只允许放行的ip地址



拦截GET, POST, PUT, DELETE请求

```java
http.authorizeRequests()
    .antMatchers(HttpMethod.GET, "/test1").authenticated()	// 拦截/test1的GET请求
    .antMatchers(HttpMethod.POST, "/test2").authenticated()   // 拦截/test2的POST请求
    .antMatchers(HttpMethod.PUT, "/test3").authenticated()   // 拦截/test3的PUT请求
    .antMatchers(HttpMethod.DELETE, "/test4").authenticated()  // 拦截/test4的DELETE请求
```



> 角色和权限的区别
>
> 1. 权限只是一个字符串，在`UserDetailsService`使用`AuthorityUtils.commaSeparatedStringToAuthorityList(String)`返回，比较的时候会把用户的权限拿出来和配置的比较
> 2. 角色会在比较的时候把``hasAuthority(String)`和``hasRole(String)`设置的字符串前面加上`ROLE_`再比较，所以使用角色要在`UserDetailsService`设置权限时加上`ROLE_`



设置用户Access Denied的处理器

实现`AccessDeniedHandler`接口

然后设置

```java
http.exceptionHandling()
	.accessDeniedHandler(myAccessDeniedHandler);
```



除了在配置类中使用还可以使用注解实现，首先要在配置类中开启注解

```java
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MySecurityConfig extends WebSecurityConfigurerAdapter {
   ...
}
```

然后再 ` *UserDetailService* ` 中给用户添加角色或权限

```java
@Configuration
public class MyUserDetailService implements UserDetailsService {
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 模拟一个用户，替代数据库获取逻辑
        MyUser user = new MyUser();
        user.setUserName(username);
        user.setPassword(this.passwordEncoder.encode("123456"));

        // 判断是什么用户并赋予对应的权限
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (StringUtils.equalsIgnoreCase("root", username)) {
            authorities = AuthorityUtils.commaSeparatedStringToAuthorityList("admin");
        } else {
            authorities = AuthorityUtils.commaSeparatedStringToAuthorityList("test");
        }
        return new User(username, user.getPassword(), user.isEnabled(),
                user.isAccountNonExpired(), user.isCredentialsNonExpired(),
                user.isAccountNonLocked(), authorities);
    }
}
```

在Controller中使用 `@PreAuthorize` 判断权限，如果判断角色那么就要把 `hasAuthority(...)` 换成 `hasRole('ROLE_USER')` 

```java
@GetMapping("/auth/admin")
@PreAuthorize("hasAuthority('admin')")
public String adminPage() {
    return "admin page";
}
```

`@PreAuthorize` 可以填写spEL表达式，比如可以验证表单提交的长度

```java
@PreAuthorize("hasRole('ROLE_USER') and #form.note.length() <= 1000 or hasRole('ROLE_VIP')")
```

这里要求如果是 `ROLE_USER` 用户填写表单时 `note` 字数小于1000，如果是 `ROLE_VIP` 则没有字数限制 

除了 `@PreAuthorize` 还可以使用 `@PostAuthorize` 在方法执行完后检查返回值

```java
@PostAuthorize("returnObject.user.userName == userList.username")
```



### Spring Security + Thymeleaf

首先导入Spring Security和Thymeleaf的整合包

```xml
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity4</artifactId>
    <version>3.0.4.RELEASE</version>
</dependency>
```



[Thymeleaf + Spring Security官方文档]( https://www.thymeleaf.org/doc/articles/springsecurity.html )

[整合包Github地址]( https://github.com/thymeleaf/thymeleaf-extras-springsecurity )

[spring security CSRF防护]( https://blog.csdn.net/yjclsx/article/details/80349906 )

然后再html引入命名空间

```xml
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:th="https://www.thymeleaf.org"
      xmlns:sec="https://www.thymeleaf.org/thymeleaf-extras-springsecurity3">
```

使用sec标签判断用户是否登陆或者是否有特定角色

```html
<div sec:authorize="isAuthenticated()">
  This content is only shown to authenticated users.
</div>
<div sec:authorize="hasRole('ROLE_ADMIN')">
  This content is only shown to administrators.
</div>
<div sec:authorize="hasRole('ROLE_USER')">
  This content is only shown to users.
</div>

<!-- 取出用户名 -->
<span sec:authentication="name"></span>
<!-- 获取登陆用户的所有角色 -->
<span sec:authentication="principal.authorities"></span>
```

使用CSRF认证时除了自己加一个hidden属性外还可以使用这种方法

```html
<form method="post" action="/do/something">
    <sec:csrfInput />
    Name:<br />
    <input type="text" name="name" />
    ...
</form>
```

Thymeleaf中使用` #authentication `操控Spring Security中的Authentication对象

也可以在 `<form>` 中这样写

```html
<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
```

如果使用AJAX提交表单则可以在 `<meta>` 中获取令牌然后再AJAX请求中提提交

 ```html
<html>
    <head>
        <meta name=“_csrf” content=“${_csrf.token}” />
        <meta name=“_csrf_header” content=“${_csrf.headerName}” />
    </head>
    
    <body>
        <!-- TODO -->
        <script>
        	var token = $("meta[name='_csrf']").attr("content");
            var header = $("meta[name='_csrf_header']").attr("content");
            $.ajax({
                url:url,
                type:'POST',
                async:false,
                dataType:'json',    //返回的数据格式：json/xml/html/script/jsonp/text
                beforeSend: function(xhr) {
                    xhr.setRequestHeader(header, token);  //发送请求前将csrfToken设置到请求头中
                },
                success:function(data,textStatus,jqXHR){
                }
            });
        </script>
    </body>
</html>
 ```