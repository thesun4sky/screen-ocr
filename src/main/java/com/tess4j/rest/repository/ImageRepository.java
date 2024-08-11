package com.tess4j.rest.repository;

import com.tess4j.rest.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Long> {

  List<Image> findByUserId(String userId);
}