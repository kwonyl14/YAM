package com.ssafy.yam.domain.user.service;

//import com.ssafy.yam.auth.Provider.JwtTokenProvider;
import com.ssafy.yam.auth.Provider.RandomSaltProvider;
import com.ssafy.yam.domain.bookmark.entity.Bookmark;
import com.ssafy.yam.domain.bookmark.repository.BookmarkRepository;
import com.ssafy.yam.domain.deal.entity.Deal;
import com.ssafy.yam.domain.deal.repository.DealRepository;
import com.ssafy.yam.domain.image.repository.ImageRepository;
import com.ssafy.yam.domain.item.entity.Item;
import com.ssafy.yam.domain.item.repository.ItemRepository;
import com.ssafy.yam.domain.user.dto.request.UserRequestDto;
import com.ssafy.yam.domain.user.dto.response.UserResponseDto;
import com.ssafy.yam.domain.user.entity.User;
import com.ssafy.yam.domain.user.enums.Role;
import com.ssafy.yam.domain.user.repository.UserRepository;
import com.ssafy.yam.utils.ResponseUtils;
import com.ssafy.yam.utils.S3UploadUtils;
import com.ssafy.yam.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static com.ssafy.yam.utils.ConstantsUtils.FROM_EMAIL_ADDRESS;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final DealRepository dealRepository;
    private final ItemRepository itemRepository;
    private final ImageRepository imageRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ResponseUtils response;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ModelMapper modelMapper;
    private RandomSaltProvider randomSaltProvider;
//    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @Transactional
    public ResponseEntity<?> signUp(UserRequestDto.SignUp signUp) {
        if(userRepository.existsByUserEmail(signUp.getUserEmail())) {
            return response.fail("?????? ??????????????? email ?????????.", HttpStatus.BAD_REQUEST);
        }

        String salt = randomSaltProvider.getNextSalt().toString();

        User user = User.builder()
                .userNickname(signUp.getUserNickname())
                .userEmail(signUp.getUserEmail())
                .userPassword(passwordEncoder.encode(signUp.getUserPassword() + salt))
//                .userPassword(passwordEncoder.encode(signUp.getUserPassword()))
                .userSalt(salt)
                .userImageUrl("https://yam-s3.s3.ap-northeast-2.amazonaws.com/profile/defaultImage.png")
                .userAuthLevel(1)
                .build();
        userRepository.save(user);

        return response.success("??????????????? ??????????????????.");
    }

//    public ResponseEntity<?> login(UserRequestDto.Login login) {
//        if(!userRepository.existsByUserEmail(login.getUserEmail())) {
//            return response.fail("???????????? ????????? ???????????? ????????????.", HttpStatus.BAD_REQUEST);
//        }
//
//        // 1. Login ID/PW ??? ???????????? Authentication ?????? ??????
//        // ?????? authentication ??? ?????? ????????? ???????????? authenticated ?????? false
//        UsernamePasswordAuthenticationToken authenticationToken = login.toAuthentication();
//
//        // 2. ?????? ?????? (????????? ???????????? ??????)??? ??????????????? ??????
//        // authenticate ???????????? ????????? ??? CustomUserDetailsService ?????? ?????? loadUserByUsername ???????????? ??????
//        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
//
//        // 3. ?????? ????????? ???????????? JWT ?????? ??????
//        UserResponseDto.TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);
//
//        // TODO:: RefreshToken Redis ??????
//
//        return response.success(tokenInfo, "???????????? ??????????????????.", HttpStatus.OK);
//    }

    public boolean emailCheck(String userEmail) {
        return userRepository.existsByUserEmail(userEmail);
    }

    public UserResponseDto.SendEmailResDto sendEmail(String userEmail) {
        UserResponseDto.SendEmailResDto sendEmailResDto = new UserResponseDto.SendEmailResDto();

        // ???????????? ??????
        String key = certificationNumberGenerator();
        // ?????? ??????
        UserResponseDto.EmailResDto mail = createEmail(userEmail, key);
        // ?????? ??????
        mailSend(mail);
        sendEmailResDto.setCertificationNumber(key);

        return sendEmailResDto;
    }

    @Autowired
    private JavaMailSender mailSender;
    public void mailSend(UserResponseDto.EmailResDto emailDto) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emailDto.getEmail());
        message.setFrom(FROM_EMAIL_ADDRESS);
        message.setSubject(emailDto.getTitle());
        message.setText(emailDto.getMessage());
        mailSender.send(message);

    }

    public UserResponseDto.EmailResDto createEmail(String userEmail, String certificationNumber) {
        UserResponseDto.EmailResDto emailResDto = new UserResponseDto.EmailResDto();
        emailResDto.setEmail(userEmail);
        emailResDto.setTitle("YAM ???????????? ?????? ?????? ?????? ?????????.");
        emailResDto.setMessage("???????????????. YAM ???????????? ?????? ?????? ?????? ?????????." + "\n" + "???????????? ??????????????? " + certificationNumber + "?????????.");

        return emailResDto;
    }

    public String certificationNumberGenerator(){

        char[] charSet = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
                'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };

        StringBuilder sb = new StringBuilder();
        int idx = 0;

        for (int i = 0; i < 6; i++) {
            idx = (int) (charSet.length * Math.random());
            sb.append(charSet[idx]);
        }
        return sb.toString();
    }

    @Autowired
    private S3UploadUtils s3UploadUtils;

    @Transactional
    public UserResponseDto.ModifyProfileResDto modifyProfile(String token, MultipartFile userImage, String userNickname) {
        UserResponseDto.ModifyProfileResDto modifyProfileResDto = new UserResponseDto.ModifyProfileResDto(false, false);
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        String userSet = tokenEmail + "(" + LocalDate.now().toString() + ")";
        String imageUrl = null;

        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        if(userImage != null) {
            try {
                imageUrl = s3UploadUtils.upload(userImage, "profile", userSet);
                logger.info(tokenEmail + " : profile image upload s3 success");
            } catch (IOException e){
                logger.info(tokenEmail + " : profile image upload s3 fail");
                e.printStackTrace();
            }
        }

        if(imageUrl != null){
            user.setUserImageUrl(imageUrl);
            modifyProfileResDto.setModifiedImage(true);
        }
        if(userNickname != null){
            user.setUserNickname(userNickname);
            modifyProfileResDto.setModifiedNickname(true);
        }

        userRepository.save(user);

        return modifyProfileResDto;
    }

    public UserResponseDto.ShowProfileResDto showProfile(String token) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        UserResponseDto.ShowProfileResDto showProfileResDto = modelMapper.map(user, UserResponseDto.ShowProfileResDto.class);

        return showProfileResDto;
    }

    @Transactional
    public boolean modifyAddress(String token, UserRequestDto.ModifyAddress modifyAddress) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        user.setUserAddress(modifyAddress.getUserAddress());
        user.setUserAreaCode(modifyAddress.getUserAreaCode());
        userRepository.save(user);

        // token ??? ??????????????? ?????? ?????? ?????? ?????? ???????????? ??????.
        return true;
    }

    public UserResponseDto.ScheduleResDto getSchedule(String token, String userDate) {
        LocalDate requestDate = LocalDate.parse(userDate);
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        UserResponseDto.ScheduleResDto scheduleResDto = new UserResponseDto.ScheduleResDto();
        int currentMonth = requestDate.getMonthValue();

        List<Deal> dealList = dealRepository.findByBuyer_UserIdOrSeller_UserId(user.getUserId(), user.getUserId());
        List<UserResponseDto.GiveResDto> giveList = new ArrayList<>();
        List<UserResponseDto.TakeResDto> takeList = new ArrayList<>();

        scheduleResDto.set??????????????????(getScheduledDate(dealList, currentMonth));

        for (int i = 0; i < dealList.size(); i++) {
            // ???????????? ????????? ?????? ?????? ????????? ??????/???????????? ??????
            if(dealList.get(i).getDealEndDate().getMonthValue() == currentMonth || dealList.get(i).getDealStartDate().getMonthValue() == currentMonth) {
                if(dealList.get(i).getSeller().getUserId() == user.getUserId()) {
                    // ?????? ???????????? ????????? == take
                    UserResponseDto.TakeResDto tmpTake = new UserResponseDto.TakeResDto();
                    Item tmpItem = itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId());
                    tmpTake.setItemId(tmpItem.getItemId());
                    tmpTake.setItemName(tmpItem.getItemName());
                    tmpTake.setItemBuyerNickname(userRepository.findByUserId(dealList.get(i).getBuyer().getUserId()).get().getUserNickname());
                    tmpTake.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmpItem.getItemId()));
                    tmpTake.setDealStartDate(dealList.get(i).getDealStartDate());
                    tmpTake.setDealEndDate(dealList.get(i).getDealEndDate());
                    takeList.add(tmpTake);
                } else {
                    // ?????? ???????????? ?????? ????????? == give
                    UserResponseDto.GiveResDto tmpGive = new UserResponseDto.GiveResDto();
                    Item tmpItem = itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId());
                    tmpGive.setItemId(tmpItem.getItemId());
                    tmpGive.setItemName(tmpItem.getItemName());
                    tmpGive.setItemSellerNickname(userRepository.findByUserId(dealList.get(i).getSeller().getUserId()).get().getUserNickname());
                    tmpGive.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmpItem.getItemId()));
                    tmpGive.setDealStartDate(dealList.get(i).getDealStartDate());
                    tmpGive.setDealEndDate(dealList.get(i).getDealEndDate());
                    giveList.add(tmpGive);
                }

            } else dealList.remove(i);
        }

        scheduleResDto.set????????????(giveList);
        scheduleResDto.set????????????(takeList);

        return scheduleResDto;
    }

    public List<LocalDate> getScheduledDate(List<Deal> dealList, int currentMonth) {
        // ?????? ?????? ???????????? ?????? ?????? ????????????, ?????? ?????? ????????? ?????? ????????? ?????? ??????
        List<LocalDate> dateList = new ArrayList<>();
        HashSet<LocalDate> dateSet = new HashSet<>();
        for (int i = 0; i < dealList.size(); i++) {
            if(dealList.get(i).getDealStartDate().getMonthValue() == currentMonth && dealList.get(i).getDealEndDate().getMonthValue() ==currentMonth) {
                // ?????? ???????????? ???????????? ?????? ??? ?????? ????????? ??????
                LocalDate pivotDate = dealList.get(i).getDealStartDate();
                while(pivotDate.isBefore(dealList.get(i).getDealEndDate().plusDays(1))) {
                    dateSet.add(pivotDate);
                    pivotDate = pivotDate.plusDays(1);
                }
            } else if(dealList.get(i).getDealStartDate().getMonthValue() == currentMonth) {
                // ?????? ???????????? ?????? ????????????, ?????? ???????????? ???????????? ?????? ??????
                LocalDate pivotDate = dealList.get(i).getDealStartDate();
                while(pivotDate.getMonthValue() == currentMonth) {
                    dateSet.add(pivotDate);
                    pivotDate = pivotDate.plusDays(1);
                }
            } else if(dealList.get(i).getDealEndDate().getMonthValue() == currentMonth) {
                // ?????? ???????????? ?????? ????????????, ?????? ???????????? ???????????? ?????? ??????
                LocalDate pivotDate = dealList.get(i).getDealEndDate();
                while(pivotDate.getMonthValue() == currentMonth){
                    dateSet.add(pivotDate);
                    pivotDate = pivotDate.minusDays(1);
                }
            } else continue;
        }

        for(LocalDate date : dateSet) {
            dateList.add(date);
        }
        Collections.sort(dateList);
        return dateList;
    }

    public List<UserResponseDto.GetGiveItemResDto> getGiveItem(String token) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.GetGiveItemResDto> giveItemList = new ArrayList<>();
        List<Item> itemList = itemRepository.findAllBySeller_UserIdOrderByItemModifiedTime(user.getUserId());
        for (int i = 0; i < itemList.size(); i++) {
            UserResponseDto.GetGiveItemResDto tmp = modelMapper.map(itemList.get(i), UserResponseDto.GetGiveItemResDto.class);
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmp.getItemId()));
            giveItemList.add(tmp);
        }

        return giveItemList;
    }

    public List<UserResponseDto.GetTakeItemResDto> getTakeItem(String token) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.GetTakeItemResDto> takeItemList = new ArrayList<>();
        List<Deal> dealList = dealRepository.findByBuyer_UserIdOrderByDealStartDate(user.getUserId());
        for (int i = 0; i < dealList.size(); i++) {
            UserResponseDto.GetTakeItemResDto tmp = modelMapper.map(dealList.get(i), UserResponseDto.GetTakeItemResDto.class);
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(dealList.get(i).getItem().getItemId()));
            tmp.setItemAddress(itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId()).getItemAddress());
            tmp.setItemName(itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId()).getItemName());
            takeItemList.add(tmp);
        }

        return takeItemList;
    }

    public List<UserResponseDto.GetItemHistoryResDto> getItemHistory(String token, int itemid) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.GetItemHistoryResDto> historyList = new ArrayList<>();
        List<Deal> dealList = dealRepository.findAllByItem_ItemId(itemid);
        for (int i = 0; i < dealList.size(); i++) {
            UserResponseDto.GetItemHistoryResDto tmp = modelMapper.map(dealList.get(i), UserResponseDto.GetItemHistoryResDto.class);
            tmp.setItemBuyerImage(userRepository.findByUserId(dealList.get(i).getBuyer().getUserId()).get().getUserImageUrl());
            tmp.setItemBuyerNickname(userRepository.findByUserId(dealList.get(i).getBuyer().getUserId()).get().getUserNickname());
            historyList.add(tmp);
        }

        return historyList;
    }

    public UserResponseDto.Receipt getReceipt(String token, int dealId) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        Deal deal = dealRepository.findByDealId(dealId).get();
        UserResponseDto.Receipt receipt = modelMapper.map(deal, UserResponseDto.Receipt.class);
        receipt.setItemName(deal.getItem().getItemName());
        receipt.setItemBuyerNickname(deal.getBuyer().getUserNickname());
        receipt.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(deal.getItem().getItemId()));
        receipt.setItemAddress(deal.getItem().getItemAddress());
        receipt.setItemPrice(deal.getItem().getItemPrice());

        return receipt;
    }

    public List<UserResponseDto.WishList> getWishList(String token) {
        String tokenEmail = TokenUtils.getUserEmailFromToken(token);
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.WishList> wishList = new ArrayList<>();
        List<Bookmark> bookmarkList = bookmarkRepository.findAllByBookmarkId_UserId(user.getUserId());
        for (int i = 0; i < bookmarkList.size(); i++) {
            Item item = itemRepository.findItemByItemId(bookmarkList.get(i).getBookmarkId().getItemId());
            UserResponseDto.WishList tmp = modelMapper.map(item, UserResponseDto.WishList.class);
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmp.getItemId()));
            wishList.add(tmp);
        }

        return wishList;
    }
}
