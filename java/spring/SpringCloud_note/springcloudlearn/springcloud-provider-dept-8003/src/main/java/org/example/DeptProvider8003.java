package org.example;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@MapperScan("org.example.mapper")
@EnableDiscoveryClient
@EnableEurekaClient
public class DeptProvider8003 {

    public static void main( String[] args ) {
        SpringApplication.run(DeptProvider8003.class, args);
    }
}
