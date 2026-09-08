package com.shilov.ecommerce.productservice.repository;

import com.shilov.ecommerce.productservice.document.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Boolean existsBySku(String sku);

    Page<Product> findByActiveTrue(Pageable pageable);

}
