package com.handcraftshop.model;

public class Seller {
    private int sellerId;
    private String shopName;
    private String ownerName;
    private String email;
    private String phone;
    private String password;
    private String sellerLocation;
    private String approvalStatus;
    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSellerLocation() { return sellerLocation; }
    public void setSellerLocation(String sellerLocation) { this.sellerLocation = sellerLocation; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
}
