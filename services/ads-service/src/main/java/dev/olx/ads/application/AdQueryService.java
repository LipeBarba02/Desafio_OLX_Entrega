package dev.olx.ads.application;

import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.domain.Category;
import dev.olx.ads.persistence.AdEntity;
import dev.olx.ads.persistence.AdRepository;
import dev.olx.ads.web.AdResponse;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdQueryService {
    private static final int MAX_PAGE_SIZE=100;
    private final AdRepository ads;
    public AdQueryService(AdRepository ads){this.ads=ads;}
    @Transactional(readOnly=true)
    public Page<AdResponse> list(String category,int page,int size){
        Pageable pageable=PageRequest.of(Math.max(0,page),Math.max(1,Math.min(size,MAX_PAGE_SIZE)),Sort.by(Sort.Direction.DESC,"createdAt"));
        Page<AdEntity> result=(category==null||category.isBlank())?ads.findByStatus(AdStatus.APPROVED,pageable):ads.findByStatusAndCategory(AdStatus.APPROVED,Category.fromCode(category.strip()),pageable);
        return result.map(AdResponse::from); // map while transaction is open; image collection is lazy.
    }
    @Transactional(readOnly=true)
    public AdResponse get(UUID id){return ads.findById(id).map(AdResponse::from).orElseThrow(()->new AdNotFoundException(id));}
}
