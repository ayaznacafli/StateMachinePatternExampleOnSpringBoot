package com.example.service.impl;

import com.example.dto.OrderDto;
import com.example.model.Order;
import com.example.model.OrderStatus;
import com.example.repository.OrderRepository;
import com.example.service.OrderService;
import com.example.service.actions.StatusAction;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OrderServiceImpl implements OrderService {

    private Map<String, StatusAction> statusActionMap;

    private final ModelMapper modelMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderRepository orderRepository;

    public OrderServiceImpl(List<StatusAction> statusActions, ModelMapper modelMapper,ApplicationEventPublisher applicationEventPublisher,OrderRepository orderRepository) {
        this.modelMapper = modelMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.orderRepository = orderRepository;
        initActions(statusActions);
    }

    @Override
    public OrderDto addOrder(OrderDto orderDto) {
        Order order = modelMapper.map(orderDto,Order.class);
        order.setOrderStatus(OrderStatus.NEW);
        return modelMapper.map(orderRepository.save(order),OrderDto.class);
    }

    @Transactional
    @Override
    public OrderDto updateOrder(OrderDto orderDto, String status) {
        StatusAction statusAction = statusActionMap.get(status);
        if(statusAction == null) {
            throw new IllegalArgumentException("Unknown action: " + status);
        }
        return orderRepository.findById(orderDto.getId())
                .map(order -> {
                    checkAllowed(statusAction,order.getOrderStatus());
                    statusAction.applyProcessing(modelMapper.map(order, OrderDto.class));
                    return updateStatus(order,statusAction.getOrderStatus());
                })
                .map(o -> modelMapper.map(o, OrderDto.class))
                .orElseThrow(() -> new IllegalArgumentException("Unknown order: "+orderDto.getId()));
    }

    private Order updateStatus(Order order, OrderStatus updateStatus) {
        order.setOrderStatus(updateStatus);
        return orderRepository.save(order);
    }

    private void checkAllowed(StatusAction statusAction, OrderStatus orderStatus) {
        Set<OrderStatus> allowedSourceStatuses = Stream.of(OrderStatus.values())
                .filter(status -> status.getStatuses().contains(statusAction.getStatusName()))
                .collect(Collectors.toSet());
        if(!allowedSourceStatuses.contains(orderStatus)) {
            throw new RuntimeException("The transition from the "+ orderStatus.name() +" status to the "
                                       + statusAction.getOrderStatus().name() + " status is not allowed");
        }
    }

    private void initActions(List<StatusAction> statusActions) {
        Map<String,StatusAction> actionMap = new HashMap<>();
        for (StatusAction statusAction : statusActions) {
            if(actionMap.containsKey(statusAction.getStatusName())) {
                throw new IllegalStateException("Dublicate transition"+statusAction.getStatusName());
            }
            actionMap.put(statusAction.getStatusName(), statusAction);
        }
        statusActionMap = Collections.unmodifiableMap(actionMap);
    }
}
