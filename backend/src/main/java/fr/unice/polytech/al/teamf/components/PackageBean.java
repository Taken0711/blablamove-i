package fr.unice.polytech.al.teamf.components;

import fr.unice.polytech.al.teamf.ComputePoints;
import fr.unice.polytech.al.teamf.ManagePackage;
import fr.unice.polytech.al.teamf.entities.GPSCoordinate;
import fr.unice.polytech.al.teamf.entities.Mission;
import fr.unice.polytech.al.teamf.entities.Parcel;
import fr.unice.polytech.al.teamf.entities.User;
import fr.unice.polytech.al.teamf.exceptions.UnknownUserException;
import fr.unice.polytech.al.teamf.notifier.Notifier;
import fr.unice.polytech.al.teamf.repositories.MissionRepository;
import fr.unice.polytech.al.teamf.repositories.ParcelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional
@Component
public class PackageBean implements ManagePackage {
    
    RabbitTemplate rabbitTemplate;
    private Notifier notifier = Notifier.getInstance();
    
    @Autowired
    ComputePoints computePoints;
    @Autowired
    MissionRepository missionRepository;
    @Autowired
    ParcelRepository parcelRepository;
    
    public PackageBean(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    @Override
    public boolean missionFinished(Mission mission) {
        try {
            computePoints.computePoints(mission);
            mission.setFinished(); // useless for the moment, but may be useful if we want to keep a history
            mission.getTransporter().removeTransportedMission(mission);
            mission.getOwner().removeOwnedMission(mission);
            parcelRepository.delete(mission.getParcel());
            missionRepository.delete(mission);
            return true;
        } catch (UnknownUserException e) {
            log.error(e.getMessage());
        }
        return false;
    }
    
    @Override
    public boolean dropPackageToHost(User host, Parcel parcel) {
        log.trace("PackageBean.dropPackage");
        parcel.setKeeper(host);
        notifier.notifyUser(parcel.getOwner(), buildDroppedPackageMessage(host.getName()), rabbitTemplate);
        return true;
    }
    
    @Override
    public boolean takePackageFromHost(User newDriver, Parcel parcel) {
        log.trace("PackageBean.takePackage");
        // We dont care about coordinates here
        Mission mission = new Mission(newDriver, parcel.getOwner(), new GPSCoordinate(42, 42), new GPSCoordinate(69, 69), parcel);
        missionRepository.save(mission);
        parcel.setMission(mission);
        parcel.setKeeper(newDriver);
        mission.setOngoing();
        notifier.notifyUser(mission.getParcel().getOwner(), buildTakenPackageMessage(newDriver.getName()), rabbitTemplate);
        return newDriver.addTransportedMission(mission);
    }
    
    @Override
    public boolean takePackageFromDriver(User newDriver, Mission mission) {
        log.trace("PackageBean.takePackage");
        mission.setOngoing();
        notifier.notifyUser(mission.getOwner(), buildChangeDriverMessage(newDriver.getName()), rabbitTemplate);
        return true;
    }
    
    static String buildDroppedPackageMessage(String hostName) {
        return String.format("Your package has been dropped to %s's house !", hostName);
    }
    
    static String buildTakenPackageMessage(String newDriverName) {
        return String.format("Your package has been taken by %s from the temporary location !", newDriverName);
    }
    
    static String buildChangeDriverMessage(String newDriverName) {
        return String.format("%s has taken your package !", newDriverName);
        
    }
}
