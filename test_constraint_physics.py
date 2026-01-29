"""
Simulate rope constraint physics to find proper spring-damper values
that prevent oscillation while maintaining constraint.
"""
import numpy as np

# Constants
GRAVITY = 32.0  # blocks/s²
DT = 0.05  # Assume 20 ticks/second
ROPE_LENGTH = 5.0  # Example rope length

def simulate_constraint(spring_k, damping_c, natural_freq=None, damping_ratio=None, 
                        initial_distance=6.0, initial_radial_vel=0.0, num_ticks=200):
    """
    Simulate constraint behavior with given parameters.
    
    Returns: (distances, radial_velocities, times)
    """
    # Calculate spring and damping if using natural frequency approach
    if natural_freq is not None:
        effective_mass = 1.0
        spring_k = effective_mass * natural_freq * natural_freq
        if damping_ratio is not None:
            damping_c = 2.0 * damping_ratio * np.sqrt(spring_k * effective_mass)
    
    distance = initial_distance
    radial_velocity = initial_radial_vel
    
    distances = [distance]
    radial_velocities = [radial_velocity]
    times = [0.0]
    
    for tick in range(num_ticks):
        excess_distance = distance - ROPE_LENGTH
        
        if excess_distance > 0.1:  # Constraint tolerance
            # Spring-damper: desired radial velocity
            # v_desired = -k*x - c*v_current
            spring_velocity = -spring_k * excess_distance
            damping_velocity = -damping_c * radial_velocity
            desired_radial_velocity = spring_velocity + damping_velocity
            
            # Apply velocity change (limit to prevent extreme corrections)
            max_velocity_change = 3.0
            velocity_change = desired_radial_velocity - radial_velocity
            
            if abs(velocity_change) > max_velocity_change:
                velocity_change = np.sign(velocity_change) * max_velocity_change
            
            radial_velocity += velocity_change
        else:
            # Within rope length - apply gravity
            radial_velocity -= GRAVITY * DT
        
        # Update position
        distance += radial_velocity * DT
        
        # Prevent negative distance
        if distance < 0:
            distance = 0
            radial_velocity = 0
        
        distances.append(distance)
        radial_velocities.append(radial_velocity)
        times.append((tick + 1) * DT)
    
    return np.array(distances), np.array(radial_velocities), np.array(times)

def find_optimal_values():
    """Test different parameter combinations to find ones that don't oscillate."""
    print("Testing constraint parameters...")
    print("=" * 60)
    
    # Test different approaches
    test_cases = [
        # (spring_k, damping_c, description)
        (10.0, 5.0, "Low spring, low damping"),
        (20.0, 10.0, "Medium spring, medium damping"),
        (30.0, 15.0, "Higher spring, higher damping"),
        (40.0, 20.0, "High spring, high damping"),
        (50.0, 25.0, "Very high spring, very high damping"),
        # Natural frequency approach
        (None, None, "Natural freq=5, damping=1.0", 5.0, 1.0),
        (None, None, "Natural freq=8, damping=1.2", 8.0, 1.2),
        (None, None, "Natural freq=10, damping=1.5", 10.0, 1.5),
        (None, None, "Natural freq=6, damping=2.0", 6.0, 2.0),
    ]
    
    best_case = None
    best_oscillation = float('inf')
    
    for case in test_cases:
        if len(case) == 5:  # Natural frequency case
            _, _, desc, nat_freq, damp_ratio = case
            spring_k, damping_c = None, None
        else:
            spring_k, damping_c, desc = case
            nat_freq, damp_ratio = None, None
        
        distances, radial_vels, times = simulate_constraint(
            spring_k, damping_c, nat_freq, damp_ratio,
            initial_distance=6.0, initial_radial_vel=0.0, num_ticks=200
        )
        
        # Calculate oscillation metric (variance in last half of simulation)
        second_half = distances[len(distances)//2:]
        oscillation = np.std(second_half)
        final_distance = distances[-1]
        final_excess = abs(final_distance - ROPE_LENGTH)
        
        print(f"{desc:40s} | Oscillation: {oscillation:.4f} | Final excess: {final_excess:.4f}")
        
        if oscillation < best_oscillation and final_excess < 0.5:
            best_oscillation = oscillation
            if len(case) == 5:
                best_case = (None, None, desc, nat_freq, damp_ratio)
            else:
                best_case = case
    
    print("=" * 60)
    if best_case:
        print(f"\nBest case: {best_case[2] if len(best_case) == 3 else best_case[2]}")
        if len(best_case) == 5:
            print(f"  Natural frequency: {best_case[3]}")
            print(f"  Damping ratio: {best_case[4]}")
        else:
            print(f"  Spring constant: {best_case[0]}")
            print(f"  Damping coefficient: {best_case[1]}")
    
    return best_case

def analyze_simulation(spring_k, damping_c, natural_freq=None, damping_ratio=None):
    """Analyze simulation results."""
    distances, radial_vels, times = simulate_constraint(
        spring_k, damping_c, natural_freq, damping_ratio,
        initial_distance=6.0, initial_radial_vel=0.0, num_ticks=200
    )
    
    # Calculate metrics
    second_half = distances[len(distances)//2:]
    oscillation = np.std(second_half)
    final_distance = distances[-1]
    final_excess = abs(final_distance - ROPE_LENGTH)
    max_overshoot = np.max(distances) - ROPE_LENGTH
    settling_time = None
    
    # Find when it settles (within 0.1 of rope length)
    for i, d in enumerate(distances):
        if abs(d - ROPE_LENGTH) < 0.1:
            settling_time = times[i]
            break
    
    return {
        'oscillation': oscillation,
        'final_excess': final_excess,
        'max_overshoot': max_overshoot,
        'settling_time': settling_time,
        'distances': distances,
        'times': times
    }

if __name__ == "__main__":
    # Find optimal values
    best = find_optimal_values()
    
    # Analyze the best case
    if best:
        print("\nAnalyzing best case...")
        if len(best) == 5:
            result = analyze_simulation(None, None, best[3], best[4])
        else:
            result = analyze_simulation(best[0], best[1])
        
        print(f"  Oscillation (std dev): {result['oscillation']:.4f}")
        print(f"  Final excess distance: {result['final_excess']:.4f}")
        print(f"  Max overshoot: {result['max_overshoot']:.4f}")
        if result['settling_time']:
            print(f"  Settling time: {result['settling_time']:.2f}s")
        else:
            print(f"  Settling time: Did not settle")
