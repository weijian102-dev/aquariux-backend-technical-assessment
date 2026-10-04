package com.aquariux.technical.assessment.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.aquariux.technical.assessment.trade.entity.CryptoPair;

@Mapper
public interface CryptoPairMapper {
    
    @Select("""
            SELECT id FROM crypto_pairs WHERE pair_name = #{pairName}
            """)
    Long findIdByPairName(String pairName);

    @Select("""
        SELECT
            id,
            base_symbol_id AS baseSymbolId,
            quote_symbol_id AS quoteSymbolId,
            pair_name AS pairName,
            active
        FROM crypto_pairs
        WHERE pair_name = #{pairName}
          AND active = TRUE
        """)
    CryptoPair findByPairName(String pairName);
}