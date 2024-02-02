package com.mathewzvk.orderservice.service;

import com.mathewzvk.orderservice.dto.InventoryResponse;
import com.mathewzvk.orderservice.dto.OrderLineItemsDto;
import com.mathewzvk.orderservice.dto.OrderRequest;
import com.mathewzvk.orderservice.event.OrderPlacedEvent;
import com.mathewzvk.orderservice.model.Order;
import com.mathewzvk.orderservice.model.OrderLineItems;
import com.mathewzvk.orderservice.repo.OrderRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {


    private final OrderRepository orderRepository;

    private final WebClient.Builder webClientBuilder;

    private final ObservationRegistry observationRegistry;

    private final ApplicationEventPublisher applicationEventPublisher;

    public String placeOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream().map(this::mapToOrderLineItems).toList();

        order.setOrderLineItems(orderLineItems);
        List<String> skuCodes = order.getOrderLineItems().stream()
                        .map(OrderLineItems::getSkuCode)
                                .toList();

        Observation inventoryServiceObservation = Observation.createNotStarted("inventory-service-lookup", this.observationRegistry);
        inventoryServiceObservation.lowCardinalityKeyValue("call", "inventory-service");
        return inventoryServiceObservation.observe(() -> {

            InventoryResponse[] inventoryResponsesArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();

            assert inventoryResponsesArray != null;
            boolean allItemIsInStock = Arrays.stream(inventoryResponsesArray).allMatch(InventoryResponse::isInStock);

            if(allItemIsInStock){
                orderRepository.save(order);
                applicationEventPublisher.publishEvent(new OrderPlacedEvent(this, order.getOrderNumber()));
                return "Order placed Successfully!!";
            }else {
                throw new IllegalArgumentException("Product is not in the Stock, please try again later!!");
            }
                }
        );

    }

    private OrderLineItems mapToOrderLineItems(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        return orderLineItems;
    }


}
