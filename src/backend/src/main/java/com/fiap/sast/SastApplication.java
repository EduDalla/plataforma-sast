package com.fiap.sast;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling public class SastApplication { public static void main(String[] args) { SpringApplication.run(SastApplication.class,args); } }
